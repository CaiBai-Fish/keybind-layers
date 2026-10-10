package caibai_fish.keybindlayers.fabric;

import caibai_fish.keybindlayers.client.KeybindLayersClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric 1.20.1 入口。
 *
 * <p>只做一件事：把 config 目录交给共享的初始化逻辑。</p>
 *
 * <h2>为什么这里什么都不注入</h2>
 * <p>Forge / NeoForge 侧会在入口装两个东西——修饰键读取器与「修饰键冲突」判据
 * （{@code KeyModifiers}）、以及按键自己声明的作用域（{@code KeyContexts}）。
 * 这两个都是加载器的扩展：<strong>Fabric 上原版 {@code KeyMapping} 既没有修饰键，
 * 也没有作用域声明</strong>，冲突判定只有 {@code same} 一条。所以 Fabric 什么都不装，
 * 共享层会自动退回「原版判定 + 配置与未知分类策略」。</p>
 *
 * <p>用 {@link ClientModInitializer} 而不是 {@code ModInitializer}：本类引用了
 * {@link KeybindLayersClient}，后者触碰 {@code net.minecraft.client.KeyMapping} 等
 * 客户端类，专用服务器上不能加载它。{@code environment: "client"} 加上 client
 * entrypoint 之后，服务端根本不会碰这个类。</p>
 */
public final class KeybindLayersFabric implements ClientModInitializer {

    public static final String MOD_ID = "keybindlayers";

    @Override
    public void onInitializeClient() {
        KeybindLayersClient.init(FabricLoader.getInstance().getConfigDir());
    }
}
