package caibai_fish.keybindlayers.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code config/keybindlayers.json} 的数据模型。
 *
 * <p>字段用 Gson 直接序列化，因此全部为 public、可空。缺失字段由
 * {@link #normalize()} 补上默认值——玩家手工删掉某个字段不应该让 mod 崩掉。</p>
 */
public final class LayerConfig {

    /** 总开关。false 时完全退回原版的冲突检测。 */
    public boolean enabled = true;

    /** 未知分类的归层策略，取值为 {@link UnknownCategoryPolicy} 的 {@code configName()}。 */
    public String unknownCategoryPolicy = UnknownCategoryPolicy.OWN_LAYER.configName();

    /**
     * 分类 → 层级 id。默认写入原版 7 个分类，玩家可改可删；
     * 删掉的分类会退回 {@link #unknownCategoryPolicy} 的兜底规则。
     */
    public Map<String, String> categories = new LinkedHashMap<>();

    /** 单键覆盖：{@code 分类|按键名} → 层级 id，用于把某个分类里的个别按键单独挪层。 */
    public Map<String, String> mappings = new LinkedHashMap<>();

    /** 打开后在日志里打印每次层级判定的结果，排查整合包问题时用。 */
    public boolean logLayerDecisions = false;

    /**
     * 「整合包作者」模式开关：在层级管理界面里打开后，可以创建 / 修改 / 删除层级。
     *
     * <p>关闭时管理界面为只读，避免普通玩家误删整合包作者精心分好的层级。</p>
     */
    public boolean packAuthorMode = false;

    /**
     * 是否把组合键（如 {@code Shift+W}）与单键（{@code W}）分开看待，默认 {@code true}。
     *
     * <p>原版会把两者判为冲突，但按 {@code W} 并不会触发 {@code Shift+W}，玩家把两者绑到
     * 不同功能通常是有意的组合。设为 {@code false} 可退回原版的标红行为。</p>
     */
    public boolean separateModifierCombinations = true;

    /**
     * 是否采用按键<strong>自己声明</strong>的生效作用域，默认 {@code true}。
     *
     * <p>NeoForge 的 {@code IKeyConflictContext} 允许 mod 声明一个键是只在游戏内生效
     * （{@code IN_GAME}）还是只在界面里生效（{@code GUI}），这正好就是本 mod 的「操作层级」。
     * 开启后：配置里显式写过的按键仍以配置为准，没写过的才看这个声明，
     * 声明是 {@code UNIVERSAL}（默认值）或自定义 context 时继续走未知分类策略。</p>
     *
     * <p>1.20.1 侧没有注入这个读取器（Forge 的 context 在另一个包），因此那边此项无效果。</p>
     */
    public boolean useDeclaredKeyContext = true;

    /**
     * 显式定义过的层级：层级 id → 定义（显示名与深度）。
     *
     * <p>内置的 8 个层级不写进这里也始终存在；表里出现的层若与内置同名，则只取它的
     * 显示名（内置层级的深度不允许被覆盖）。</p>
     */
    public Map<String, LayerDefinition> layers = new LinkedHashMap<>();

    /**
     * 生成一份带内置默认分层的配置。
     *
     * <p>除原版 7 个分类的归属外，还会写入 {@link DefaultAdaptations} 里的默认适配：
     * 原版个别按键按实际生效场合重新归层，以及若干主流 mod 的分类归属。</p>
     */
    public static LayerConfig defaults() {
        LayerConfig config = new LayerConfig();
        KeyLayers.DEFAULT_CATEGORY_LAYERS.forEach((category, layer) -> config.categories.put(category, layer.id()));
        DefaultAdaptations.applyTo(config);
        return config;
    }

    /** 补齐 null 字段，并把策略字符串规范化。 */
    public void normalize() {
        if (this.categories == null) {
            this.categories = new LinkedHashMap<>();
        }
        if (this.mappings == null) {
            this.mappings = new LinkedHashMap<>();
        }
        if (this.layers == null) {
            this.layers = new LinkedHashMap<>();
        }
        this.layers.values().removeIf(definition -> definition == null);
        this.layers.forEach((id, definition) -> definition.normalize(id));
        this.unknownCategoryPolicy = UnknownCategoryPolicy.fromName(this.unknownCategoryPolicy).configName();
    }
}
