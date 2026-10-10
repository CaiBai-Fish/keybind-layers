package caibai_fish.keybindlayers.neoforge.client;

import caibai_fish.keybindlayers.client.KeyContexts;
import caibai_fish.keybindlayers.client.KeyMappingLayers;
import caibai_fish.keybindlayers.client.KeyModifiers;
import caibai_fish.keybindlayers.client.LayerManagerScreen;
import caibai_fish.keybindlayers.client.ModNames;
import caibai_fish.keybindlayers.core.KeyLayers;
import caibai_fish.keybindlayers.neoforge.KeybindLayersNeoForge;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.Optional;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;

/**
 * 客户端专属入口：挂上「模组列表 → Config」的界面，并把 NeoForge 特有的三样能力注入共享层。
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

        installKeyModifierSupport();
        installModNameResolver();
        installKeyContextReader();
        // 采样器是 NeoForge 特有的（要读 IKeyConflictContext），Fabric 侧不装。
        KeyContexts.installResampler(InGameContextSampler::sampleNow);
    }

    /**
     * 把 NeoForge 特有的修饰键能力接到共享层。
     *
     * <p>{@code shared/} 不能 import 平台类，而 {@code KeyModifier} 的包名在 Forge 与
     * NeoForge 上还不一样，所以一律走注入。Fabric 侧不装这些，自动退回原版判定。</p>
     */
    private static void installKeyModifierSupport() {
        KeyModifiers.install(KeyMapping::getKeyModifier);
        KeyModifiers.installConflictCheck(KeyMapping::hasKeyModifierConflict);
        // 原版的 key 字段是 private；getKey() 是 NeoForge 自己加的 getter。
        KeyModifiers.installKeyReader(KeyMapping::getKey);
        KeyModifiers.installCombos(new KeyModifiers.Combos() {
            @Override
            public boolean supported() {
                return true;
            }

            @Override
            public boolean isModifierKey(InputConstants.Key key) {
                return KeyModifier.isKeyCodeModifier(key);
            }

            @Override
            public Object modifierFor(InputConstants.Key key) {
                return KeyModifier.getKeyModifier(key);
            }

            @Override
            public Object none() {
                return KeyModifier.NONE;
            }

            @Override
            public void bind(KeyMapping mapping, Object modifier, InputConstants.Key key) {
                // modifierFor 有且只有两种返回：一个 KeyModifier，或 none()。
                mapping.setKeyModifierAndCode(
                        modifier instanceof KeyModifier keyModifier ? keyModifier : KeyModifier.NONE, key);
            }
        });
    }

    /**
     * 把「mod id → 显示名」的查询接到共享层。
     *
     * <p>自建界面的标签名要用 mod 显示名而不是 {@code key.category.*} 那种分类名，
     * 但查询方式各加载器完全不同，所以由平台注入。</p>
     */
    private static void installModNameResolver() {
        ModNames.install(modId -> {
            Optional<? extends ModContainer> container = ModList.get().getModContainerById(modId);
            return container.map(modContainer -> modContainer.getModInfo().getDisplayName()).orElse(null);
        });
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
     * {@link ModNames} 就能命中；命中失败返回 {@code null}，调用方会退回类简单名。</p>
     */
    private static String modDisplayNameOf(IKeyConflictContext context) {
        String packageName = context.getClass().getPackageName();
        for (String part : packageName.split("\\.")) {
            String displayName = ModNames.displayNameOf(part);
            if (displayName != null) {
                return displayName;
            }
        }
        return null;
    }
}
