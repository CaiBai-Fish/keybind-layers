package caibai_fish.keybindlayers.core;

/**
 * 一条「层级定义」，即玩家 / 整合包作者在配置里显式写下的层级。
 *
 * <p>它是 {@link KeyLayer} 的可序列化影子：{@code KeyLayer} 是不可变运行时对象，
 * 而本类直接参与 Gson 序列化，因此字段全部 public、可空，缺失值由
 * {@link #normalize(String)} 补齐。</p>
 *
 * <p>只有<strong>显式定义过的层级</strong>才会出现在配置里。内置的 8 个层级
 * （见 {@link KeyLayers}）即使不写进配置也始终存在；对内置层级，本类只用来承载
 * 玩家改过的显示名。</p>
 */
public final class LayerDefinition {

    /** 新建层级时的默认深度，介于 interface(200) 与 mods(300) 之间。 */
    public static final int DEFAULT_ORDER = 250;
    /** 深度允许范围：只影响「控制」界面的标签排序与列表分组顺序。 */
    public static final int MIN_ORDER = 0;
    public static final int MAX_ORDER = 1000;

    /**
     * 层级 id：小写字母、数字与下划线，全局唯一。
     *
     * <p>反序列化时以 JSON 的键为准，这里仅作冗余保存。</p>
     */
    public String id;

    /**
     * 自定义显示名，可为 {@code null}。
     *
     * <p>为空时界面回退到语言文件条目 {@code keybindlayers.layer.<id>}；
     * 再找不到就直接显示 id。整合包作者可以用它给自定义层级起中文名，
     * 而不必附带资源包。</p>
     */
    public String displayName;

    /** 深度：数值越小越「底层」，仅用于排序与展示，不参与冲突判定。 */
    public int order = DEFAULT_ORDER;

    /** Gson 需要无参构造。 */
    public LayerDefinition() {
    }

    public LayerDefinition(String id, String displayName, int order) {
        this.id = id;
        this.displayName = displayName;
        this.order = order;
    }

    /** 按 id 与深度构造，显示名留空（走语言文件）。 */
    public static LayerDefinition of(String id, int order) {
        return new LayerDefinition(id, null, order);
    }

    /**
     * 补齐缺失字段。
     *
     * @param fallbackId JSON 键里的层级 id；{@link #id} 为空或与键冲突时以它为准
     */
    public void normalize(String fallbackId) {
        if (fallbackId != null && !fallbackId.isEmpty()) {
            this.id = fallbackId;
        }
        if (this.displayName != null) {
            String trimmed = this.displayName.trim();
            this.displayName = trimmed.isEmpty() ? null : trimmed;
        }
        if (this.order < MIN_ORDER) {
            this.order = MIN_ORDER;
        } else if (this.order > MAX_ORDER) {
            this.order = MAX_ORDER;
        }
    }

    /** 该定义指的是内置层级（内置层级的深度不允许被改动）。 */
    public boolean isBuiltin() {
        return KeyLayers.isBuiltin(this.id);
    }

    /** 转成运行时层级对象。 */
    public KeyLayer toLayer() {
        return new KeyLayer(this.id, this.order);
    }

    /** 复制一份，避免编辑器直接改动已应用的配置对象。 */
    public LayerDefinition copy() {
        return new LayerDefinition(this.id, this.displayName, this.order);
    }

    @Override
    public String toString() {
        return "LayerDefinition[" + this.id + ", order=" + this.order
                + (this.displayName == null ? "" : ", name=" + this.displayName) + "]";
    }
}
