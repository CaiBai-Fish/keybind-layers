package caibai_fish.keybindlayers.neoforge.mixin;

import caibai_fish.keybindlayers.client.KeyMappingLayers;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 让「控制」设置界面的冲突提示变成层级感知的（Minecraft 1.21.1 / NeoForge）。
 *
 * <p>与 Forge 1.20.1 版本的逻辑完全相同，只有 mixin 目标类的包名不同：
 * 1.21.1 把它挪到了 {@code net.minecraft.client.gui.screens.options.controls}。</p>
 *
 * <p>原版判据（{@code KeyBindsList$KeyEntry#refreshEntry()}）：</p>
 *
 * <pre>{@code
 * if ((keymapping != this.key && this.key.same(keymapping)) || keymapping.hasKeyModifierConflict(this.key)) {
 * }</pre>
 *
 * <p>字节码依据（javap 核实）：{@code same} 与 {@code hasKeyModifierConflict} 均为
 * {@code invokevirtual}，owner 都是 {@code net/minecraft/client/KeyMapping}；
 * 全游戏仅此一处调用。NeoForge 1.21.1 运行时使用 Mojmap，因此本配置不需要 refmap，
 * 但 {@code hasKeyModifierConflict} 仍显式写 {@code remap = false}：它是 NeoForge 自有
 * 接口（{@code IKeyMappingExtension}）上的 default 方法，不参与任何重映射。</p>
 *
 * <p>{@code require = 0}：注入点漂移时静默失效而不崩溃，详见 Forge 侧同类注释。</p>
 */
@Mixin(KeyBindsList.KeyEntry.class)
public class KeyBindsListKeyEntryMixin {

    @Redirect(
            method = "refreshEntry()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/KeyMapping;same(Lnet/minecraft/client/KeyMapping;)Z"),
            require = 0)
    private boolean keybindlayers$sameWithinLayer(KeyMapping self, KeyMapping other) {
        return self.same(other) && KeyMappingLayers.reportConflict(self, other);
    }

    @Redirect(
            method = "refreshEntry()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/KeyMapping;hasKeyModifierConflict(Lnet/minecraft/client/KeyMapping;)Z",
                    remap = false),
            require = 0)
    private boolean keybindlayers$modifierConflictWithinLayer(KeyMapping self, KeyMapping other) {
        return self.hasKeyModifierConflict(other) && KeyMappingLayers.reportConflict(self, other);
    }
}
