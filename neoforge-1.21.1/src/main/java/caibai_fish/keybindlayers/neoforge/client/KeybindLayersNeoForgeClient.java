package caibai_fish.keybindlayers.neoforge.client;

import caibai_fish.keybindlayers.client.KeyContexts;
import caibai_fish.keybindlayers.client.KeyMappingLayers;
import caibai_fish.keybindlayers.core.KeyLayers;
import caibai_fish.keybindlayers.neoforge.KeybindLayersNeoForge;
import java.util.Optional;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/**
 * 客户端专属入口：挂上「模组列表 → Config」的界面，并注入按键作用域的读取器。
 *
 * <h2>为什么单独开一个入口类</h2>
 * <p>{@code IConfigScreenFactory} 的方法签名引用了 {@code Screen}，而
 * {@code net.minecraft.client.gui.screens.Screen} 带类级 {@code @OnlyIn(Dist.CLIENT)}；
 * FML 的 {@code RuntimeDistCleaner} 在专用服务器上加载这类会直接抛异常。
 * 用 {@code @Mod(dist = Dist.CLIENT)} 声明后，服务端根本不会加载本类
 * （NeoForge 自身也是「一个 modId 两个入口类」这种形态）。</p>
 *
 * <p>注意 {@code ModContainer#registerExtensionPoint} 对同一个接口只会保留最后一次注册，
 * 因此一个 mod 只能有一个配置界面；层级管理界面的子页要在屏幕内部导航。</p>
 */
@Mod(value = KeybindLayersNeoForge.MOD_ID, dist = Dist.CLIENT)
public final class KeybindLayersNeoForgeClient {

    public KeybindLayersNeoForgeClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (modContainer, modListScreen) -> new LayerManagerScreen(modListScreen));

        installKeyContextReader();
    }

    /**
     * 把按键声明的生效作用域接到层级模型上。
     *
     * <p>{@code IN_GAME} 的键只在游戏里生效（会与移动、攻击同时响应），
     * {@code GUI} 的键只在界面里生效。{@code UNIVERSAL}（默认值）等于没有声明，返回
     * {@code null} 让它继续走配置与未知分类策略。</p>
     *
     * <p><b>自定义 context 单独成层</b>：很多 mod 自己实现 {@code IKeyConflictContext}，
     * 判断的是「当前屏幕是不是我自己的界面」。这类作用域读不出标准值，但它本身就是作者
     * 写下的声明——直接拿它的类型名当一个专属层即可。这样同一个界面的按键同层
     * （彼此仍会报冲突），而不同 mod 的界面天然不同层：它们的界面永远不会同时打开，
     * 压成同一个 {@code interface} 层反而会制造假冲突。</p>
     *
     * <p>这里只登记一个 lambda，不会在构造期触碰 {@code KeyMapping} 实例。</p>
     */
    private static void installKeyContextReader() {
        KeyContexts.install(mapping -> {
            IKeyConflictContext context = mapping.getKeyConflictContext();
            if (context == KeyConflictContext.IN_GAME) {
                return KeyLayers.GAMEPLAY.id();
            }
            if (context == KeyConflictContext.GUI) {
                return KeyLayers.INTERFACE.id();
            }
            if (context == KeyConflictContext.UNIVERSAL) {
                return null;
            }
            // 采样发现「不开界面也活跃」的（条件只是装备/手持某物）并进游戏内层。
            if (KeyContexts.isInGameContext(context)) {
                return KeyLayers.GAMEPLAY.id();
            }
            // 其余自定义 context 按类型各自成层，标签名用它所属 mod 的显示名
            // （例如 JEI 的 19 个按键分属多个细分类，拿分类名当标签名会变成
            // 「JEI（鼠标悬停）」那种以偏概全的叫法）。
            String layerId = KeyMappingLayers.CONTEXT_LAYER_PREFIX + context.getClass().getName();
            String displayName = modDisplayNameOf(context);
            if (displayName != null) {
                KeyLayers.setDisplayName(layerId, displayName);
            }
            return layerId;
        });
    }

    /**
     * 从 context 类所在的包名里认出它属于哪个 mod，返回 mod 的显示名。
     *
     * <p>mod 的实现类通常放在 {@code net.<作者>.<modid>.…} 这样的包下，逐段拿去问
     * {@link ModList} 就能命中；命中失败返回 {@code null}，调用方会退回类简单名。</p>
     */
    private static String modDisplayNameOf(IKeyConflictContext context) {
        String packageName = context.getClass().getPackageName();
        for (String part : packageName.split("\\.")) {
            Optional<? extends ModContainer> container = ModList.get().getModContainerById(part);
            if (container.isPresent()) {
                return container.get().getModInfo().getDisplayName();
            }
        }
        return null;
    }
}
