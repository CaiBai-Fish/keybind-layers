package caibai_fish.keybindlayers.neoforge;

import caibai_fish.keybindlayers.client.KeybindLayersClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;

/**
 * NeoForge 1.21.1 入口。
 *
 * <p>与 Forge 侧完全相同的职责：客户端环境下读取 config 并初始化层级规则。</p>
 */
@Mod(KeybindLayersNeoForge.MOD_ID)
public final class KeybindLayersNeoForge {

    public static final String MOD_ID = "keybindlayers";

    public KeybindLayersNeoForge() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            KeybindLayersClient.init(FMLPaths.CONFIGDIR.get());
        }
    }
}
