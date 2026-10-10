package caibai_fish.keybindlayers.fabric.mixin;

import caibai_fish.keybindlayers.client.LayerKeyBindsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 把「选项 → 控制 → 按键设置」的入口换成层级感知的界面（Minecraft 1.21.1 / Fabric）。
 *
 * <h2>入口链</h2>
 * <p>1.21.1 的入口不是 {@code OptionsScreen} 直连 {@code KeyBindsScreen}，中间还有一层
 * {@code ControlsScreen}（列出「鼠标设置」「按键设置」）。真正构造按键界面的地方是它的
 * 按钮回调：</p>
 *
 * <pre>{@code
 * Button.builder(Component.translatable("controls.keybinds"),
 *         button -> this.minecraft.setScreen(new KeyBindsScreen(this, this.options)))
 * }</pre>
 *
 * <h2>为什么不能用 NeoForge 侧那种注入点</h2>
 * <p>NeoForge 用 Mojmap，{@code ControlsScreen} 里那个回调保留着 javac 生成的
 * {@code lambda$addOptions$1} 方法名，所以那边可以直接按名字注入（已实机验证）。
 * <strong>Fabric 不行</strong>：Minecraft 发布的是混淆 jar，合成方法在官方映射里没有条目，
 * 实际运行时它们叫 {@code method_60340} / {@code method_60341}——而且哪个对应「按键设置」
 * 还得靠猜。Loom 在 remap 时会明确警告：</p>
 *
 * <pre>
 * Cannot remap lambda$addOptions$1(Lnet/minecraft/client/gui/components/Button;)V
 * because it does not exist in any of the targets [ControlsScreen]
 * </pre>
 *
 * <p>所以这里改注入 {@code Minecraft#setScreen} 的入参：只要「当前屏幕是控制页」且
 * 「正要打开的是原版按键界面」，就换成自建界面。判据不依赖任何合成方法名，
 * 也不依赖 {@code addOptions} 的内部结构。</p>
 *
 * <p>{@code require = 0}：原版若重构 {@code setScreen} 的签名，本 mod 静默退回原版界面
 * 而不是崩溃。排查时把 {@code require} 改成 1。</p>
 */
@Mixin(Minecraft.class)
public class MinecraftSetScreenMixin {

    @ModifyVariable(
            method = "setScreen",
            at = @At("HEAD"),
            argsOnly = true,
            require = 0)
    private Screen keybindlayers$replaceKeyBindsScreen(Screen screen) {
        if (screen instanceof KeyBindsScreen && Minecraft.getInstance().screen instanceof ControlsScreen) {
            Minecraft minecraft = Minecraft.getInstance();
            // 用当前屏幕（ControlsScreen）当返回目标，Esc/完成后回到「控制」页。
            return new LayerKeyBindsScreen(minecraft.screen, minecraft.options);
        }
        return screen;
    }
}
