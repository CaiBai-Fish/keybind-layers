package caibai_fish.keybindlayers.core;

import java.util.Locale;

/**
 * 当某个按键分类既不在配置里、也不在内置默认分层里时，如何为它决定层级。
 *
 * <p>原版分类之外的分类几乎都来自 mod（{@code key.categories.modid} 或
 * {@code key.categories.mods}）。整合包里它们动辄上百个，因此这个策略决定了
 * 整合包的实际观感。</p>
 */
public enum UnknownCategoryPolicy {

    /**
     * 每个未知分类各自成为一个独立层级（默认）。
     *
     * <p>效果：分类之间永远不会互相报冲突，同一个分类内部的同键仍然会报。
     * 这是「消除不同操作层级的假冲突」最彻底的选项。</p>
     */
    OWN_LAYER,

    /**
     * 所有未知分类共用一个 {@code mods} 层。
     *
     * <p>效果：不同 mod 之间绑定同一个键时仍然会报冲突。若你认为「不同 mod 的按键
     * 大概率在同一时刻都会响应」，就用这个更保守的策略。</p>
     */
    SHARED_LAYER,

    /**
     * 所有分类都视为同一个层级。
     *
     * <p>效果：完全退回原版的全按键冲突检测。可作为「这个 mod 出问题时」的开关。</p>
     */
    STRICT;

    /** 配置值（小写下划线）→ 枚举，无法识别时回退到 {@link #OWN_LAYER}。 */
    public static UnknownCategoryPolicy fromName(String name) {
        if (name == null) {
            return OWN_LAYER;
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        for (UnknownCategoryPolicy policy : values()) {
            if (policy.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return policy;
            }
        }
        return OWN_LAYER;
    }

    /** 枚举 → 配置文件里使用的小写下划线形式。 */
    public String configName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
