package caibai_fish.keybindlayers.client;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.minecraft.client.KeyMapping;

/**
 * 读取按键<strong>自己声明</strong>的生效作用域。
 *
 * <h2>这是什么</h2>
 * <p>Forge / NeoForge 给 {@code KeyMapping} 加了一个 {@code IKeyConflictContext}，由 mod 在注册
 * 按键时声明这个键「在什么场合生效」。最常用的两个取值是：</p>
 * <ul>
 *   <li>{@code KeyConflictContext.IN_GAME}：只在游戏里（没有打开界面）生效；</li>
 *   <li>{@code KeyConflictContext.GUI}：只在打开界面时生效。</li>
 * </ul>
 *
 * <p>这正好就是本 mod 所说的「操作层级」，而且是 mod 作者自己写的，比我们猜要准。
 * 实测（1.21.1，装 JEI / JourneyMap / Mekanism / Xaero's 时）97 个 mod 按键里有 59 个
 * 声明了明确作用域，约六成。</p>
 *
 * <h2>为什么是注入式</h2>
 * <p>两个加载器的 {@code KeyConflictContext} 枚举位于不同包，而本文件在共用
 * {@code shared/} 里，不能 import 任一版本。因此由平台侧注入一个「按键 → 层级 id」的
 * 转换器（1.21.1 在 {@code KeybindLayersNeoForgeClient} 里注入）。</p>
 *
 * <p>没有注入、或按键用的是 {@code UNIVERSAL} / 自定义 context 时返回 {@code null}，
 * 调用方会退回配置与未知策略。</p>
 */
public final class KeyContexts {

    /** 平台注入的转换器：按键 → 层级 id；返回 {@code null} 表示「没有可用的声明」。 */
    private static volatile Function<KeyMapping, String> resolver;

    /**
     * 已判定为「不开界面也能生效」的自定义作用域类型。
     *
     * <p>这类 context 的额外条件只是「装备/手持了某样东西」，在游戏进行中随时可用，
     * 所以该并进游戏内层，而不是单开一个标签。</p>
     */
    private static final Set<Class<?>> IN_GAME_CONTEXTS = ConcurrentHashMap.newKeySet();

    private KeyContexts() {
    }

    /**
     * 记录一个「游戏内也能生效」的自定义作用域。
     *
     * <p>判定办法：在没有任何界面打开时调用它的 {@code isActive()}。为 {@code true} 就说明
     * 它不要求打开界面——例如 Sophisticated Backpacks 的 {@code BackpackKeyConflictContext}：
     * 其实现是「没有界面时为 true；有界面时仅当那是容器界面才为 true」，
     * 也就是装备/手持背包即可用，属于游戏内。</p>
     */
    public static void markInGameContext(Object context) {
        if (context != null) {
            IN_GAME_CONTEXTS.add(context.getClass());
        }
    }

    /** 该作用域是否已被判定为「不开界面也能生效」。 */
    public static boolean isInGameContext(Object context) {
        return context != null && IN_GAME_CONTEXTS.contains(context.getClass());
    }

    /** 由平台客户端入口注入；传 {@code null} 可撤销。 */
    public static void install(Function<KeyMapping, String> layerIdResolver) {
        resolver = layerIdResolver;
    }

    /**
     * 平台侧「立刻重新采样一次作用域」的动作；Fabric 不注入。
     *
     * <p>采样本身依赖各加载器的 context 体系（NeoForge 上是
     * {@code InGameContextSampler}），共享层碰不到，所以只留一个钩子。</p>
     */
    private static volatile Runnable resampler;

    /** 由平台客户端入口注入；传 {@code null} 可撤销。 */
    public static void installResampler(Runnable action) {
        resampler = action;
    }

    /**
     * 请求平台立刻重采一次作用域。
     *
     * <p>打开按键界面时调一次：玩家可能刚进游戏就直接开设置，
     * 还没等到常规采样时机，此时自定义 context 的分层会不准。</p>
     *
     * <p>未注入（Fabric）或采样抛异常时静默跳过——采样只是锦上添花，
     * 不该因此打不开界面。</p>
     */
    public static void requestResample() {
        Runnable current = resampler;
        if (current == null) {
            return;
        }
        try {
            current.run();
        } catch (RuntimeException e) {
            // 采样失败不影响界面打开。
        }
    }

    /** 该按键声明的作用域对应的层级 id；没有可用声明时返回 {@code null}。 */
    public static String layerIdOf(KeyMapping mapping) {
        Function<KeyMapping, String> current = resolver;
        if (current == null || mapping == null) {
            return null;
        }
        try {
            return current.apply(mapping);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
