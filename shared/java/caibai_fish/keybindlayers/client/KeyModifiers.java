package caibai_fish.keybindlayers.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.BiPredicate;
import java.util.function.Function;
import net.minecraft.client.KeyMapping;

/**
 * 读取按键绑定的「修饰键」（组合键前缀，例如 {@code Shift+W} 里的 Shift）、平台对
 * 「修饰键冲突」的判定，以及读写组合键的能力。
 *
 * <h2>为什么需要这层间接</h2>
 * <p>Forge 与 NeoForge 的 {@code KeyModifier} 枚举位于不同包
 * （{@code net.minecraftforge.client.settings} 与
 * {@code net.neoforged.neoforge.client.settings}），而本文件位于各工程共用的
 * {@code shared/}，不能 import 任一版本；<strong>Fabric 上这些概念根本不存在</strong>
 * ——原版 {@code KeyMapping} 只有一个 {@code key} 字段，冲突判定也只有 {@code same} 一条。</p>
 *
 * <p>因此由平台入口在客户端初始化时装上下面这些能力。Fabric 什么都不装，于是每一处都
 * 安全降级：{@link #of} 返回 {@code null}、{@link #sameModifier} 返回 {@code true}、
 * {@link #hasModifierConflict} 返回 {@code false}、{@link #combosSupported} 返回
 * {@code false}，{@link #bind} 退化成 {@code KeyMapping#setKey}。</p>
 */
public final class KeyModifiers {

    /**
     * 平台读写组合键的能力。
     *
     * <p>界面里所有「按下 Shift 再按某键」的逻辑都走它：Fabric 上
     * {@link #supported()} 为 {@code false}，界面自然只做单键绑定。</p>
     */
    public interface Combos {

        /** 平台是否支持组合键。 */
        boolean supported();

        /** 这个键码是不是修饰键（Shift / Ctrl / Alt / …）。 */
        boolean isModifierKey(InputConstants.Key key);

        /** 键 → 平台的修饰键对象；不是修饰键时返回 {@link #none()}。 */
        Object modifierFor(InputConstants.Key key);

        /** 「没有修饰键」的表示。 */
        Object none();

        /** 把修饰键与主键一起写进绑定。 */
        void bind(KeyMapping mapping, Object modifier, InputConstants.Key key);
    }

    /** 不支持组合键时的实现：界面只做单键绑定。 */
    private static final Combos NO_COMBOS = new Combos() {
        @Override
        public boolean supported() {
            return false;
        }

        @Override
        public boolean isModifierKey(InputConstants.Key key) {
            return false;
        }

        @Override
        public Object modifierFor(InputConstants.Key key) {
            return null;
        }

        @Override
        public Object none() {
            return null;
        }

        @Override
        public void bind(KeyMapping mapping, Object modifier, InputConstants.Key key) {
            mapping.setKey(key);
        }
    };

    /** 读取一个绑定的修饰键；未注入时为 {@code null}。 */
    private static volatile Function<KeyMapping, Object> reader;

    /** 原版把「只有修饰键不同」也算冲突的那个判据；Fabric 上没有。 */
    private static volatile BiPredicate<KeyMapping, KeyMapping> conflictCheck;

    private static volatile Combos combos = NO_COMBOS;

    private KeyModifiers() {
    }

    /**
     * 注入修饰键读取器。
     *
     * @param keyModifierReader Forge / NeoForge 上是 {@code KeyMapping::getKeyModifier}；
     *                          Fabric 不调用
     */
    public static void install(Function<KeyMapping, Object> keyModifierReader) {
        reader = keyModifierReader;
    }

    /**
     * 注入「修饰键冲突」判据。
     *
     * @param check Forge / NeoForge 上是 {@code KeyMapping::hasKeyModifierConflict}；
     *              Fabric 不调用，此时 {@link #hasModifierConflict} 恒为 {@code false}
     */
    public static void installConflictCheck(BiPredicate<KeyMapping, KeyMapping> check) {
        conflictCheck = check;
    }

    /**
     * 注入组合键能力。
     *
     * @param implementation Forge / NeoForge 上是包着 {@code KeyModifier} 的一个实现；
     *                       Fabric 不调用，界面只做单键绑定
     */
    public static void installCombos(Combos implementation) {
        combos = implementation == null ? NO_COMBOS : implementation;
    }

    /** 读取「绑定当前绑定的键」的函数；未注入时为 {@code null}。 */
    private static volatile Function<KeyMapping, InputConstants.Key> boundKeyReader;

    /**
     * 注入「读取绑定当前绑定的键」。
     *
     * <p>原版的 {@code key} 字段是 private，也没有公开的读法。Forge / NeoForge 各自加了
     * {@code KeyMapping#getKey()}，Fabric 上则要靠一个 {@code @Accessor} mixin 提供。</p>
     *
     * <p>未注入时 {@link #boundKeyOf} 返回 {@link InputConstants#UNKNOWN}，
     * 依赖它的「锁定按键占位判定」会安静地不拦截——宁可少拦一次，
     * 也不要因为读不出绑定而误判。</p>
     */
    public static void installKeyReader(Function<KeyMapping, InputConstants.Key> keyReader) {
        boundKeyReader = keyReader;
    }

    /** 该绑定当前绑定的键；未注入或读取失败时返回 {@link InputConstants#UNKNOWN}。 */
    public static InputConstants.Key boundKeyOf(KeyMapping mapping) {
        Function<KeyMapping, InputConstants.Key> current = boundKeyReader;
        if (current == null || mapping == null) {
            return InputConstants.UNKNOWN;
        }
        try {
            InputConstants.Key key = current.apply(mapping);
            return key == null ? InputConstants.UNKNOWN : key;
        } catch (RuntimeException e) {
            return InputConstants.UNKNOWN;
        }
    }

    /** 该绑定的修饰键对象；未注入或读取失败时返回 {@code null}。 */
    public static Object of(KeyMapping mapping) {
        Function<KeyMapping, Object> current = reader;
        if (current == null || mapping == null) {
            return null;
        }
        try {
            return current.apply(mapping);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 两个绑定是否使用同一个修饰键。
     *
     * <p>任一侧拿不到修饰键时返回 {@code true}，也就是<strong>退回原版判定</strong>——
     * 宁可按原版标红，也不要因为读取失败而漏报真冲突。</p>
     */
    public static boolean sameModifier(KeyMapping a, KeyMapping b) {
        Object modifierA = of(a);
        Object modifierB = of(b);
        if (modifierA == null || modifierB == null) {
            return true;
        }
        return modifierA == modifierB;
    }

    /**
     * 平台是否认为这两者因修饰键而冲突（Forge / NeoForge 的
     * {@code KeyMapping#hasKeyModifierConflict}）。
     *
     * <p>未注入时返回 {@code false}。Fabric 上原版 {@code KeyMapping} 没有修饰键概念，
     * 冲突判定只有 {@code same} 一条，「没有额外的修饰键冲突」正是正确语义。</p>
     */
    public static boolean hasModifierConflict(KeyMapping a, KeyMapping b) {
        BiPredicate<KeyMapping, KeyMapping> current = conflictCheck;
        if (current == null || a == null || b == null) {
            return false;
        }
        try {
            return current.test(a, b);
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ 组合键能力

    /** 平台是否支持组合键。界面用它决定要不要走「先按修饰键」那套流程。 */
    public static boolean combosSupported() {
        return combos.supported();
    }

    /** 这个键是不是修饰键；不支持组合键时恒为 {@code false}。 */
    public static boolean isModifierKey(InputConstants.Key key) {
        try {
            return combos.supported() && combos.isModifierKey(key);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** 键 → 平台修饰键对象；不支持组合键时返回 {@code null}。 */
    public static Object modifierFor(InputConstants.Key key) {
        try {
            return combos.supported() ? combos.modifierFor(key) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 「没有修饰键」的表示；不支持组合键时为 {@code null}。 */
    public static Object noModifier() {
        return combos.none();
    }

    /**
     * 把修饰键与主键一起写进绑定。
     *
     * <p>不支持组合键、或调用方给了 {@code null} 修饰键时，退化成原版的
     * {@code KeyMapping#setKey}。</p>
     */
    public static void bind(KeyMapping mapping, Object modifier, InputConstants.Key key) {
        if (mapping == null) {
            return;
        }
        if (!combos.supported() || modifier == null) {
            mapping.setKey(key);
            return;
        }
        combos.bind(mapping, modifier, key);
    }
}
