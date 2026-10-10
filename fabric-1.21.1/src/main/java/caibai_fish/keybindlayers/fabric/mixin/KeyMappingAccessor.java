package caibai_fish.keybindlayers.fabric.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 读出 {@code KeyMapping} 当前绑定的键（Minecraft 1.21.1 / Fabric）。
 *
 * <h2>为什么需要它</h2>
 * <p>原版的 {@code key} 字段是 <strong>private</strong>，也没有公开的 getter。
 * Forge 与 NeoForge 各自加了 {@code KeyMapping#getKey()}，Fabric 没有——而共享层的
 * 「按下的键是否被某个锁定按键占用」判定需要一个统一的读法。</p>
 *
 * <p>这里用 Mixin 的 {@code @Accessor} 开一个窄口子，并在入口处把它接到
 * {@code KeyModifiers.installKeyReader}。</p>
 *
 * <p>它不是注入，只是一条 getter，不会改变原版的任何行为。</p>
 */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {

    @Accessor("key")
    InputConstants.Key keybindlayers$boundKey();
}
