package caibai_fish.keybindlayers.neoforge.mixin;

import caibai_fish.keybindlayers.neoforge.client.LayerKeyBindsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 把「选项 → 控制 → 按键设置」的入口换成层级感知的界面（Minecraft 1.21.1 / NeoForge）。
 *
 * <h2>入口链与注入点</h2>
 * <p>1.21.1 的入口不是 {@code OptionsScreen} 直连 {@code KeyBindsScreen}，中间还有一层
 * {@code ControlsScreen}（列出「鼠标设置」「按键设置」）。真正构造按键界面的地方是它的
 * 按钮回调，字节码（javap 核实）：</p>
 *
 * <pre>
 * private void lambda$addOptions$1(Button);
 *    4: new           KeyBindsScreen
 *   13: invokespecial KeyBindsScreen."&lt;init&gt;"(Screen, Options)V
 *   16: invokevirtual Minecraft.setScreen(Screen)V
 * </pre>
 *
 * <p>本 Mixin 用 {@code @ModifyArg} 把 {@code setScreen} 的实参换成本 mod 的界面。
 * 之所以不用 {@code @Redirect} 掉 {@code KeyBindsScreen.&lt;init&gt;}：构造器调用需要一个
 * 未初始化的实例引用（{@code new}/{@code dup}/{@code invokespecial} 三元组），
 * Redirect 无法安全替代。改 {@code setScreen} 的入参则完全等价且不影响
 * 「鼠标设置」那条分支（它在另一个 lambda 方法里）。</p>
 *
 * <p>{@code require = 0}：lambda 方法名由 javac 按出现顺序生成，原版重编译后可能变化，
 * 届时本 mod 静默退回原版界面而不是崩溃。排查时把 {@code require} 改成 1。</p>
 */
@Mixin(ControlsScreen.class)
public class ControlsScreenMixin {

    @ModifyArg(
            method = "lambda$addOptions$1(Lnet/minecraft/client/gui/components/Button;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"),
            index = 0,
            require = 0)
    private Screen keybindlayers$replaceKeyBindsScreen(Screen original) {
        Minecraft minecraft = Minecraft.getInstance();
        // 此刻的当前屏幕正是 ControlsScreen，用它当返回目标，Esc/完成后回到「控制」页。
        return new LayerKeyBindsScreen(minecraft.screen, minecraft.options);
    }
}
