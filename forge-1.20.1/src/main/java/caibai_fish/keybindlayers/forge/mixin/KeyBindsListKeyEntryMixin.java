package caibai_fish.keybindlayers.forge.mixin;

import caibai_fish.keybindlayers.client.KeyMappingLayers;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.controls.KeyBindsList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 让「控制」设置界面的冲突提示变成层级感知的（Minecraft 1.20.1 / Forge）。
 *
 * <p>原版 {@code KeyBindsList$KeyEntry#refreshEntry()} 用下面这一行判定冲突，
 * 其中 {@code keymapping} 是被比较的另一个绑定：</p>
 *
 * <pre>{@code
 * if ((keymapping != this.key && this.key.same(keymapping)) || keymapping.hasKeyModifierConflict(this.key)) {
 * }</pre>
 *
 * <p>本 Mixin 把这两个判据各自与「是否属于同一操作层级」相与，于是跨层级的同键
 * 不再被标红，同层级内的同键照旧标红。</p>
 *
 * <h2>为什么是 @Redirect 而不是重写整个方法</h2>
 * <p>{@code refreshEntry()} 还负责按钮文本、tooltip、选中行高亮，整段重写会随版本漂移；
 * 只替换这两个调用点，其余行为完全保持原版。</p>
 *
 * <h2>字节码依据（javap 核实，1.20.1 与 1.21.1 一致）</h2>
 * <pre>
 * 102: invokevirtual net/minecraft/client/KeyMapping.same:(Lnet/minecraft/client/KeyMapping;)Z
 * 114: invokevirtual net/minecraft/client/KeyMapping.hasKeyModifierConflict:(Lnet/minecraft/client/KeyMapping;)Z
 * </pre>
 * <p>注意 {@code hasKeyModifierConflict} 是 {@code IForgeKeyMapping} 的 default 方法，但字节码里
 * 接收者类型是 {@code KeyMapping}（invokevirtual），所以 owner 必须写 {@code net/minecraft/client/KeyMapping}。
 * 全游戏仅此一处调用，注入不会误伤。</p>
 *
 * <h2>两处映射细节</h2>
 * <ul>
 *   <li>{@code same} 会被 refmap 重映射为 {@code m_90850_}，因此保持默认 {@code remap = true}；</li>
 *   <li>{@code hasKeyModifierConflict} 是 Forge 自有接口上的方法，不在 SRG 映射表里，
 *       显式写 {@code remap = false}，避免注解处理器抱怨找不到映射。</li>
 * </ul>
 *
 * <p>{@code require = 0}：注入点因原版或 Forge 更新而移位时，本 mod 静默失效而不是让客户端崩溃。
 * 开发期想强制发现漂移，把两个注解的 {@code require} 改成 1，
 * 或把 {@code keybindlayers.mixins.json} 的 {@code injectors.defaultRequire} 改成 1。</p>
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
