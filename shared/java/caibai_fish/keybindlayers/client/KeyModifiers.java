package caibai_fish.keybindlayers.client;

import java.util.function.Function;
import net.minecraft.client.KeyMapping;

/**
 * 读取按键绑定的「修饰键」（组合键前缀，例如 {@code Shift+W} 里的 Shift）。
 *
 * <h2>为什么需要这层间接</h2>
 * <p>Forge 与 NeoForge 的 {@code KeyModifier} 枚举位于不同包
 * （{@code net.minecraftforge.client.settings} 与
 * {@code net.neoforged.neoforge.client.settings}），而本文件位于两个工程共用的
 * {@code shared/}，不能 import 任一版本。</p>
 *
 * <p>好在两边的读取方法同名（{@code KeyMapping#getKeyModifier()}），返回类型在擦除后
 * 都是 {@code Object}，因此 {@link KeyMappingLayers} 可以直接注入方法引用，
 * 而无需在两个平台各写一份代码，也无需反射。</p>
 *
 * <p>没有注入成功时 {@link #of} 返回 {@code null}，调用方会退回原版判定（安全降级）。</p>
 */
public final class KeyModifiers {

    private static volatile Function<KeyMapping, Object> reader;

    private KeyModifiers() {
    }

    /**
     * 注入修饰键读取器；由 {@link KeyMappingLayers} 的静态块在客户端首次使用时完成。
     *
     * @param keyModifierReader 一般是 {@code KeyMapping::getKeyModifier}
     */
    public static void install(Function<KeyMapping, Object> keyModifierReader) {
        reader = keyModifierReader;
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
}
