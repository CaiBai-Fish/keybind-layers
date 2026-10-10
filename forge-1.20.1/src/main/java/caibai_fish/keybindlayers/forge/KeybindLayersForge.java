package caibai_fish.keybindlayers.forge;

import caibai_fish.keybindlayers.client.KeybindLayersClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Forge 1.20.1 入口。
 *
 * <p>只做一件事：在客户端环境下把 config 目录交给共享的初始化逻辑。
 * 专用服务器上不会触碰任何客户端类（{@link KeybindLayersClient} 引用了
 * {@code net.minecraft.client.KeyMapping}，因此这个分支必须在客户端才进入）。</p>
 */
@Mod(KeybindLayersForge.MOD_ID)
public final class KeybindLayersForge {

    public static final String MOD_ID = "keybindlayers";

    public KeybindLayersForge() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            KeybindLayersClient.init(FMLPaths.CONFIGDIR.get());
        }
    }
}
