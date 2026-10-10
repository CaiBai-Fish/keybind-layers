package caibai_fish.keybindlayers.fabric.mixin;

import caibai_fish.keybindlayers.client.KeyMappingLayers;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.controls.KeyBindsList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 让「控制」设置界面的冲突提示变成层级感知的（Minecraft 1.20.1 / Fabric）。
 *
 * <p>原版 {@code KeyBindsList$KeyEntry#refreshEntry()} 用这一行判定冲突，
 * 其中 {@code keymapping} 是被比较的另一个绑定：</p>
 *
 * <pre>{@code
 * if (keymapping != this.key && this.key.same(keymapping)) {
 * }</pre>
 *
 * <p>本 Mixin 把判据与「是否属于同一操作层级」相与，于是跨层级的同键不再被标红，
 * 同层级内的同键照旧标红。</p>
 *
 * <h2>与 Forge / NeoForge 版的差别</h2>
 * <p>那两个加载器给 {@code KeyMapping} 打了补丁，判定是
 * {@code same(...) || hasKeyModifierConflict(...)}，所以它们的 Mixin 要拦两个调用点。
 * Fabric 用的是未打补丁的原版类，<strong>只有 {@code same} 一条</strong>，
 * 因此这里只拦一个——{@code hasKeyModifierConflict} 在 Fabric 上根本不存在，
 * 写上去会因为找不到目标而注入失败。</p>
 *
 * <p>反过来说，原版的 {@code same} 只比较 {@code key}（{@code InputConstants.Key}
 * 本身已带修饰键信息），所以 Fabric 上 {@code W} 与 {@code Shift+W} 本来就不算冲突，
 * 无需 {@code LayerRules.separateModifierCombinations()} 那套额外判断。</p>
 *
 * <p>{@code require = 0}：注入点因原版更新而移位时本 mod 静默失效，而不是让客户端崩溃。
 * 开发期想强制发现漂移，把 {@code require} 改成 1，或把
 * {@code keybindlayers.mixins.json} 的 {@code injectors.defaultRequire} 改成 1。</p>
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
