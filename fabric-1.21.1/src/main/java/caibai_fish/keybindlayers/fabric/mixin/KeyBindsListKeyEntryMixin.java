package caibai_fish.keybindlayers.fabric.mixin;

import caibai_fish.keybindlayers.client.KeyMappingLayers;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 让「控制」设置界面的冲突提示变成层级感知的（Minecraft 1.21.1 / Fabric）。
 *
 * <p>原版 {@code KeyBindsList$KeyEntry#refreshEntry()} 的判据只有一条，
 * 其中 {@code keymapping} 是被比较的另一个绑定：</p>
 *
 * <pre>{@code
 * if (keymapping != this.key && this.key.same(keymapping)) {
 * }</pre>
 *
 * <p>本 Mixin 把判据与「是否属于同一操作层级」相与，于是跨层级的同键不再被标红，
 * 同层级内的同键照旧标红。</p>
 *
 * <h2>与 NeoForge / Forge 版的差别</h2>
 * <p>那两个加载器给 {@code KeyMapping} 打了补丁，判据是
 * {@code same(...) || hasKeyModifierConflict(...)}，所以它们的 Mixin 要拦两个调用点。
 * Fabric 用的是未打补丁的原版类，<strong>只有 {@code same} 一条</strong>——
 * {@code hasKeyModifierConflict} 在这里根本不存在，写上去会因为找不到目标而注入失败。</p>
 *
 * <p>{@code require = 0}：注入点漂移时静默失效而不崩溃。排查时把 {@code require} 改成 1，
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
}
