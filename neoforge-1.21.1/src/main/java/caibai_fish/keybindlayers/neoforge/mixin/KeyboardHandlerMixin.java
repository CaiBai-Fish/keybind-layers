package caibai_fish.keybindlayers.neoforge.mixin;

import caibai_fish.keybindlayers.neoforge.client.LayerKeyBindsScreen;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 等待改键时不要让 F11 / F2 被当成快捷键。
 *
 * <h2>原版做了什么（javap 核实）</h2>
 * <pre>
 * 216: instanceof KeyBindsScreen
 * 219: ifeq 241        ← 不是原版界面就跳去处理快捷键
 *      （是原版界面时再比 lastKeySelection，20ms 内则跳过这整段）
 * 241: Options.keyFullscreen  → 254: KeyMapping.matches(...) → 267: Window.toggleFullScreen() → 296: return
 * 297: Options.keyScreenshot  → 310: KeyMapping.matches(...) → 342: Screenshot.grab(...)    → 345: return
 * </pre>
 *
 * <p>判据写死了原版界面类型，而我们接管「选项 → 控制」的是自建的
 * {@link LayerKeyBindsScreen}（它没法继承 {@code KeyBindsScreen}，见 docs/DESIGN.md §4.3），
 * 于是等待改键时按 F11 会连带切全屏。</p>
 *
 * <h2>为什么不是拦「切全屏」那个动作</h2>
 * <p>第一版是 {@code @Redirect} 拦住 {@code Window#toggleFullScreen} 与 {@code Screenshot#grab}。
 * 这样确实不切屏了，但**那段分支末尾的 {@code return} 照样执行**——按键被吞掉、根本没送到界面，
 * 于是界面从没记录到按下的 F11，最终把它绑成了「未设置」。用户实测报回了这个现象。</p>
 *
 * <p>正确做法是拦那两个 {@code matches}：等待改键时让它返回 false，整段分支（含 return）
 * 就被跳过了，控制流继续往下走到「把按键交给 Screen」的地方——于是 F11 会像普通键一样
 * 进入改键流程，然后由 {@code LayerKeyBindsScreen#isReservedByLockedKey} 判定它被
 * {@code key.fullscreen} 占用并拒绝、给出提示。</p>
 *
 * <p>两个 {@code matches} 的 ordinal 是 javap 数出来的：{@code keyPress} 里只有这两处调用
 * （偏移 254 与 310）。写 {@code require = 1}，原版重编译后对不上会明确崩溃而不是静默失效。</p>
 */
@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {

    /** F11：等待改键时当作「没按这个键」，跳过原版那整段。 */
    @Redirect(
            method = "keyPress",
            require = 1,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/KeyMapping;matches(II)Z",
                    ordinal = 0))
    private boolean keybindlayers$skipFullscreen(KeyMapping mapping, int keyCode, int scanCode) {
        return !keybindlayers$isRebindGuardActive() && mapping.matches(keyCode, scanCode);
    }

    /** F2：同理。 */
    @Redirect(
            method = "keyPress",
            require = 1,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/KeyMapping;matches(II)Z",
                    ordinal = 1))
    private boolean keybindlayers$skipScreenshot(KeyMapping mapping, int keyCode, int scanCode) {
        return !keybindlayers$isRebindGuardActive() && mapping.matches(keyCode, scanCode);
    }

    /**
     * 现在是否处在「玩家正在改键」的保护窗口里。
     *
     * <p>两种情况都算：</p>
     * <ol>
     *   <li><b>正在等待按键</b>（点过「改键」按钮）：这时按的任何键都可能是想绑的键。
     *       原版只按「距上次选中 20ms」判断，而那个字段只在选中时更新过一次，窗口窄得没用
     *       ——实测等待期间按 F11 照样切全屏，原版自身也有这个毛病。</li>
     *   <li>刚完成一次改键的 20ms 内，与原版的意图保持一致。</li>
     * </ol>
     */
    private static boolean keybindlayers$isRebindGuardActive() {
        if (!(Minecraft.getInstance().screen instanceof LayerKeyBindsScreen screen)) {
            return false;
        }
        return screen.isAwaitingRebind() || Util.getMillis() - screen.lastKeySelection <= 20L;
    }
}
