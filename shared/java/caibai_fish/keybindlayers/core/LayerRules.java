package caibai_fish.keybindlayers.core;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 判定核心：给定两个按键绑定，它们是否属于同一操作层级。
 *
 * <p>本 mod 只改「设置界面里是否显示冲突」，不改按键的实际触发，因此这里只回答
 * 一个问题：原版判定为冲突的两个绑定，是否应该继续显示成冲突。答案是
 * <em>仅当它们属于同一操作层级</em>。</p>
 *
 * <p>查找顺序（先命中者优先）：</p>
 * <ol>
 *   <li>单键覆盖（{@code 分类|按键名} → 层级），配置或 API 精确指定</li>
 *   <li>分类覆盖（配置、其它 mod 通过 API 指定）</li>
 *   <li>内置默认分层（原版 7 个分类）</li>
 *   <li>{@link UnknownCategoryPolicy} 决定的兜底规则</li>
 * </ol>
 *
 * <p>所有方法线程安全。</p>
 */
public final class LayerRules {

    /** 分类 → 层级 id 的显式覆盖。 */
    private static final Map<String, String> CATEGORY_LAYERS = new ConcurrentHashMap<>();
    /** {@code 分类|按键名} → 层级 id 的单键覆盖。 */
    private static final Map<String, String> MAPPING_LAYERS = new ConcurrentHashMap<>();

    /**
     * 由配置文件写入的分类覆盖键。
     *
     * <p>配置重载时只清理这些键，从而保留其它 mod 通过
     * {@link caibai_fish.keybindlayers.api.KeybindLayersApi} 设置的归属——
     * 否则玩家在层级管理界面点一次保存，就会把别的 mod 的声明抹掉。</p>
     */
    private static final Set<String> CONFIG_CATEGORY_KEYS = ConcurrentHashMap.newKeySet();
    /** 由配置文件写入的单键覆盖键。 */
    private static final Set<String> CONFIG_MAPPING_KEYS = ConcurrentHashMap.newKeySet();

    private static volatile UnknownCategoryPolicy unknownCategoryPolicy = UnknownCategoryPolicy.OWN_LAYER;
    private static volatile boolean enabled = true;
    /** 组合键（{@code Shift+W} 之类）是否与单键分开看待；详见 {@link #separateModifierCombinations()}。 */
    private static volatile boolean separateModifierCombinations = true;
    /** 是否采用按键自己声明的生效作用域；详见 {@link #useDeclaredKeyContext()}。 */
    private static volatile boolean useDeclaredKeyContext = true;

    /** 单键覆盖的键分隔符：分类里不会出现 '|'，按键名里也不会。 */
    private static final char MAPPING_SEPARATOR = '|';
    /**
     * 未知策略 {@link UnknownCategoryPolicy#OWN_LAYER} 下自动生成的层级 id 前缀。
     *
     * <p>形如 {@code category:key.categories.somemod}；界面解析显示名时靠它把临时层
     * 还原成分类本身的本地化名。</p>
     */
    public static final String OWN_LAYER_PREFIX = "category:";

    private LayerRules() {
    }

    // ---------------------------------------------------------------- 开关

    /** 关闭后 {@link #reportConflict} 恒为 true，即完全交回原版判定。 */
    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean enabled() {
        return enabled;
    }

    /**
     * 组合键（如 {@code Shift+W}）是否与单键分开看待。
     *
     * <p>{@code true}（默认）：只有修饰键相同的两个绑定才会被判为冲突，
     * 于是 {@code W} 与 {@code Shift+W} 不再互相标红；{@code false} 退回原版行为。</p>
     *
     * <p>注意这只是一个「显示判定」开关，不影响按键的实际触发。</p>
     */
    public static void setSeparateModifierCombinations(boolean value) {
        separateModifierCombinations = value;
    }

    /** 组合键是否与单键分开看待。 */
    public static boolean separateModifierCombinations() {
        return separateModifierCombinations;
    }

    /**
     * 是否采用按键自己声明的生效作用域（见 {@code client/KeyContexts}）。
     *
     * <p>开启时，配置里没有显式归属的按键会先看它声明的 {@code IN_GAME} / {@code GUI}；
     * 关闭后一律走分类映射与未知分类策略。</p>
     */
    public static void setUseDeclaredKeyContext(boolean value) {
        useDeclaredKeyContext = value;
    }

    /** 是否采用按键自己声明的生效作用域。 */
    public static boolean useDeclaredKeyContext() {
        return useDeclaredKeyContext;
    }

    public static void setUnknownCategoryPolicy(UnknownCategoryPolicy policy) {
        unknownCategoryPolicy = Objects.requireNonNull(policy, "policy");
    }

    public static UnknownCategoryPolicy unknownCategoryPolicy() {
        return unknownCategoryPolicy;
    }

    // ------------------------------------------------------------ 赋值入口

    /** 清空所有显式赋值（含其它 mod 通过 API 设置的；内置默认分层不受影响）。 */
    public static void clearAssignments() {
        CATEGORY_LAYERS.clear();
        MAPPING_LAYERS.clear();
        CONFIG_CATEGORY_KEYS.clear();
        CONFIG_MAPPING_KEYS.clear();
    }

    /**
     * 只清理由配置文件写入的赋值，保留其它 mod 通过 API 设置的归属。
     *
     * <p>配置重载与层级管理界面保存时使用——否则玩家点一次保存，就会把别的 mod
     * 在初始化阶段声明的归属一并抹掉。</p>
     */
    public static void clearConfigAssignments() {
        for (String key : CONFIG_CATEGORY_KEYS) {
            CATEGORY_LAYERS.remove(key);
        }
        CONFIG_CATEGORY_KEYS.clear();
        for (String key : CONFIG_MAPPING_KEYS) {
            MAPPING_LAYERS.remove(key);
        }
        CONFIG_MAPPING_KEYS.clear();
    }

    /** 把一个按键分类整体归入某个层级。 */
    public static void assignCategory(String category, String layerId) {
        if (category == null || category.isEmpty() || layerId == null || layerId.isEmpty()) {
            return;
        }
        CATEGORY_LAYERS.put(category, layerId);
    }

    /** 把某个分类下的某一个按键单独归入某个层级（覆盖分类级设置）。 */
    public static void assignMapping(String category, String mappingName, String layerId) {
        if (category == null || mappingName == null || layerId == null || layerId.isEmpty()) {
            return;
        }
        MAPPING_LAYERS.put(mappingKey(category, mappingName), layerId);
    }

    /** 由配置文件写入的分类归属；配置重载时会被 {@link #clearConfigAssignments()} 清掉。 */
    public static void assignCategoryFromConfig(String category, String layerId) {
        if (category == null || category.isEmpty() || layerId == null || layerId.isEmpty()) {
            return;
        }
        CATEGORY_LAYERS.put(category, layerId);
        CONFIG_CATEGORY_KEYS.add(category);
    }

    /** 由配置文件写入的单键覆盖；配置重载时会被 {@link #clearConfigAssignments()} 清掉。 */
    public static void assignMappingFromConfig(String category, String mappingName, String layerId) {
        if (category == null || mappingName == null || layerId == null || layerId.isEmpty()) {
            return;
        }
        String key = mappingKey(category, mappingName);
        MAPPING_LAYERS.put(key, layerId);
        CONFIG_MAPPING_KEYS.add(key);
    }

    /**
     * 统计有多少个分类 / 单键覆盖指向该层级。
     *
     * <p>层级管理界面在删除层级前用它提示「该层级下还有 N 处引用」。</p>
     */
    public static int countReferencesTo(String layerId) {
        if (layerId == null) {
            return 0;
        }
        int count = 0;
        for (String value : CATEGORY_LAYERS.values()) {
            if (layerId.equals(value)) {
                count++;
            }
        }
        for (String value : MAPPING_LAYERS.values()) {
            if (layerId.equals(value)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 清除所有指向该层级的分类 / 单键覆盖（删除层级时调用）。
     *
     * <p>被清掉的分类会回退到内置默认分层或 {@link UnknownCategoryPolicy} 的兜底规则，
     * 而不是继续指向一个已经不存在的层级。</p>
     *
     * @return 被清除的引用条数
     */
    public static int removeReferencesTo(String layerId) {
        if (layerId == null) {
            return 0;
        }
        Set<String> removedCategories = removeValues(CATEGORY_LAYERS, layerId);
        Set<String> removedMappings = removeValues(MAPPING_LAYERS, layerId);
        CONFIG_CATEGORY_KEYS.removeAll(removedCategories);
        CONFIG_MAPPING_KEYS.removeAll(removedMappings);
        return removedCategories.size() + removedMappings.size();
    }

    private static Set<String> removeValues(Map<String, String> map, String layerId) {
        Set<String> removed = new HashSet<>();
        for (Iterator<Map.Entry<String, String>> it = map.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, String> entry = it.next();
            if (layerId.equals(entry.getValue())) {
                removed.add(entry.getKey());
                it.remove();
            }
        }
        return removed;
    }

    /** 解析 {@code 分类|按键名} 形式的单键覆盖键，格式非法时返回 {@code null}。 */
    public static String[] parseMappingKey(String key) {
        if (key == null) {
            return null;
        }
        int split = key.indexOf(MAPPING_SEPARATOR);
        if (split <= 0 || split == key.length() - 1) {
            return null;
        }
        return new String[] { key.substring(0, split), key.substring(split + 1) };
    }

    /** 拼装单键覆盖键，供配置读写使用。 */
    public static String mappingKey(String category, String mappingName) {
        return category + MAPPING_SEPARATOR + mappingName;
    }

    // ------------------------------------------------------------ 层级解析

    /**
     * 该绑定在配置里是否有显式归属（按键级与分类级都算）。
     *
     * <p>用来决定要不要让「按键自己声明的作用域」参与判定：玩家或内置适配表显式写过的，
     * 一律以配置为准。</p>
     */
    public static boolean hasExplicitAssignment(String category, String mappingName) {
        if (category == null) {
            return false;
        }
        if (mappingName != null && MAPPING_LAYERS.containsKey(mappingKey(category, mappingName))) {
            return true;
        }
        return CATEGORY_LAYERS.containsKey(category);
    }

    /** 解析某个按键绑定的层级 id。 */
    public static String layerIdOf(String category, String mappingName) {
        if (category == null) {
            return KeyLayers.SYSTEM.id();
        }
        if (mappingName != null) {
            String override = MAPPING_LAYERS.get(mappingKey(category, mappingName));
            if (override != null) {
                return override;
            }
        }
        return layerIdOfCategory(category);
    }

    /** 解析某个按键分类的层级 id（不含单键覆盖）。 */
    public static String layerIdOfCategory(String category) {
        if (category == null) {
            return KeyLayers.SYSTEM.id();
        }
        String explicit = CATEGORY_LAYERS.get(category);
        if (explicit != null) {
            return explicit;
        }
        KeyLayer builtin = KeyLayers.defaultLayerOfCategory(category);
        if (builtin != null) {
            return builtin.id();
        }
        switch (unknownCategoryPolicy) {
            case SHARED_LAYER:
                return KeyLayers.MODS.id();
            case STRICT:
                return KeyLayers.SYSTEM.id();
            case OWN_LAYER:
            default:
                return OWN_LAYER_PREFIX + category;
        }
    }

    /** 解析某个按键绑定的层级对象；未知分类的自成层级会即时注册。 */
    public static KeyLayer layerOf(String category, String mappingName) {
        String layerId = layerIdOf(category, mappingName);
        KeyLayer layer = KeyLayers.get(layerId);
        if (layer == null) {
            // OWN_LAYER 策略下按分类临时成层：深度介于 gameplay 与 interface 之间，
            // 保证它既不会和原版游戏内按键同层，也不会和界面层同层。
            layer = KeyLayers.register(new KeyLayer(layerId, 250));
        }
        return layer;
    }

    public static KeyLayer layerOfCategory(String category) {
        return layerOf(category, null);
    }

    // ------------------------------------------------------------ 冲突判定

    /** 两个分类是否属于同一操作层级。 */
    public static boolean sameLayer(String categoryA, String categoryB) {
        return layerIdOfCategory(categoryA).equals(layerIdOfCategory(categoryB));
    }

    /** 两个具体绑定是否属于同一操作层级。 */
    public static boolean sameLayer(String categoryA, String mappingNameA,
                                    String categoryB, String mappingNameB) {
        return layerIdOf(categoryA, mappingNameA).equals(layerIdOf(categoryB, mappingNameB));
    }

    /**
     * 原版已经判定为冲突的两个绑定，是否仍然应该显示成冲突。
     *
     * <p>{@code enabled == false} 时恒为 {@code true}，即原版行为。</p>
     */
    public static boolean reportConflict(String categoryA, String mappingNameA,
                                         String categoryB, String mappingNameB) {
        if (!enabled) {
            return true;
        }
        return sameLayer(categoryA, mappingNameA, categoryB, mappingNameB);
    }
}
