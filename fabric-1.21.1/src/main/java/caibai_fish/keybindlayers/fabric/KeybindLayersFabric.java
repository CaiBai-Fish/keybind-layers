package caibai_fish.keybindlayers.fabric;

import caibai_fish.keybindlayers.client.KeyModifiers;
import caibai_fish.keybindlayers.client.KeybindLayersClient;
import caibai_fish.keybindlayers.client.ModNames;
import caibai_fish.keybindlayers.fabric.mixin.KeyMappingAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric 1.21.1 入口。
 *
 * <h2>Fabric 上装什么、不装什么</h2>
 * <p>装两样：{@link ModNames}（自建界面的标签要显示 mod 显示名，例如「精妙背包」，
 * 而不是 {@code key.category.*} 那种分类名），以及 {@code KeyModifiers} 的
 * 「读取绑定键」——原版那个字段是 private，靠 {@link KeyMappingAccessor} 读出来。</p>
 *
 * <p><strong>不装</strong>的是 Forge / NeoForge 那几个扩展：修饰键（组合键）、
 * 「修饰键冲突」判据、按键自己声明的作用域。原版 {@code KeyMapping} 既没有修饰键字段，
 * 也没有 {@code IKeyConflictContext}，冲突判定只有 {@code same} 一条。所以
 * {@code KeyModifiers} 的其余能力与 {@code KeyContexts} 都保持未注入状态，共享层会自动
 * 退回「原版判定 + 配置与未知分类策略」，界面上的组合键流程也会自动跳过
 * （见 {@code KeyModifiers.combosSupported()}）。</p>
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
        ModNames.install(modId -> FabricLoader.getInstance()
                .getModContainer(modId)
                .map(container -> container.getMetadata().getName())
                .orElse(null));

        KeyModifiers.installKeyReader(mapping -> ((KeyMappingAccessor) mapping).keybindlayers$boundKey());

        KeybindLayersClient.init(FabricLoader.getInstance().getConfigDir());
    }
}
