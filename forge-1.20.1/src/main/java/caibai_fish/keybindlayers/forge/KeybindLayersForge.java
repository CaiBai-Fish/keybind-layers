package caibai_fish.keybindlayers.forge;

import caibai_fish.keybindlayers.client.KeyModifiers;
import caibai_fish.keybindlayers.client.KeybindLayersClient;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Forge 1.20.1 入口。
 *
 * <p>做两件事：把 Forge 特有的修饰键能力接到共享层，以及在客户端环境下把 config 目录
 * 交给共享的初始化逻辑。专用服务器上不会触碰任何客户端类（{@link KeyMapping} 与
 * {@link KeybindLayersClient} 都是客户端类，因此这个分支必须在客户端才进入）。</p>
 */
@Mod(KeybindLayersForge.MOD_ID)
public final class KeybindLayersForge {

    public static final String MOD_ID = "keybindlayers";

    public KeybindLayersForge() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            installKeyModifierSupport();
            KeybindLayersClient.init(FMLPaths.CONFIGDIR.get());
        }
    }

    /**
     * 共享层（{@code shared/}）不能 import 任何加载器的类，修饰键能力一律走注入。
     *
     * <p>两个方法引用都不会在构造期触碰 {@code KeyMapping} 实例；客户端之外的分支根本
     * 不会执行到这里。Fabric 侧不装这两个，于是自动退回原版判定。</p>
     */
    private static void installKeyModifierSupport() {
        // KeyModifier 是 Forge 自有的枚举（net.minecraftforge.client.settings）
        KeyModifiers.install(KeyMapping::getKeyModifier);
        KeyModifiers.installConflictCheck(KeyMapping::hasKeyModifierConflict);
    }
}
