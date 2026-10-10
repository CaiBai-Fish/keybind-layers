package caibai_fish.keybindlayers.core;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 层级的增删改（纯逻辑，不依赖任何 Minecraft 类）。
 *
 * <p>层级管理界面只负责收集用户输入与展示错误，真正的校验与数据改动都在这里，
 * 这样规则可以单独推理与复用。</p>
 *
 * <h2>三条约定</h2>
 * <ul>
 *   <li><b>内置层级不可删除</b>：{@link KeyLayers} 里的 8 个层是判定语义的一部分，
 *       删掉会让默认分层失去落点；它们只允许改显示名，深度保持内置值。</li>
 *   <li><b>删除即回退</b>：删除一个层级时，配置里所有指向它的分类 / 单键覆盖一并移除，
 *       于是这些按键回退到内置默认分层或 {@link UnknownCategoryPolicy} 的兜底规则，
 *       而不是继续指向一个已经不存在的层级。</li>
 *   <li><b>本类只改配置对象</b>：改完由调用方（客户端配置管理）负责写盘并重新应用规则，
 *       避免纯逻辑层反过来依赖客户端生命周期。</li>
 * </ul>
 */
public final class LayerManager {

    /** 合法层级 id：小写字母、数字与下划线，且不能以数字开头。 */
    private static final String ID_PATTERN = "[a-z][a-z0-9_]*";

    private LayerManager() {
    }

    // ------------------------------------------------------------ 校验

    /**
     * 层级 id 是否合法。
     *
     * <p>非法输入（null、空串、含大写或其它字符、以数字开头）返回 {@code false}；
     * {@link #create} 前应先用它校验，以便界面直接给出提示而不是抛异常。</p>
     */
    public static boolean isValidId(String id) {
        return id != null && id.matches(ID_PATTERN);
    }

    /** id 是否已被占用（内置层级或配置里已定义）。 */
    public static boolean isTaken(LayerConfig config, String id) {
        if (id == null) {
            return false;
        }
        return KeyLayers.isBuiltin(id) || (config != null && config.layers.containsKey(id));
    }

    /** 把用户输入规范成合法 id（去空白 + 转小写）；输入为 null 时返回 {@code null}。 */
    public static String normalizeId(String id) {
        return id == null ? null : id.trim().toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------ 增删改

    /**
     * 新建一个自定义层级。
     *
     * @throws IllegalArgumentException id 非法、与内置层级同名，或配置里已经有同 id 的层级
     */
    public static LayerDefinition create(LayerConfig config, String id, String displayName, int order) {
        Objects.requireNonNull(config, "config");
        String normalized = normalizeId(id);
        if (!isValidId(normalized)) {
            throw new IllegalArgumentException("Invalid layer id: " + id);
        }
        if (KeyLayers.isBuiltin(normalized)) {
            throw new IllegalArgumentException("Layer id is reserved by a built-in layer: " + normalized);
        }
        if (config.layers.containsKey(normalized)) {
            throw new IllegalArgumentException("Layer id already exists: " + normalized);
        }
        LayerDefinition definition = new LayerDefinition(normalized, displayName, clampOrder(order));
        definition.normalize(normalized);
        config.layers.put(normalized, definition);
        return definition;
    }

    /**
     * 修改层级的显示名与深度。
     *
     * <p>内置层级也允许改显示名（会被记进配置），但深度一律忽略，保持内置值。</p>
     *
     * @throws IllegalArgumentException 该 id 既不是内置层级，也不在配置里
     */
    public static LayerDefinition update(LayerConfig config, String id, String displayName, int order) {
        Objects.requireNonNull(config, "config");
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("Layer id is empty");
        }
        LayerDefinition definition = config.layers.get(id);
        if (definition == null) {
            if (KeyLayers.isBuiltin(id)) {
                // 内置层级第一次被改动时，才在配置里建一条只承载显示名的记录。
                definition = LayerDefinition.of(id, KeyLayers.require(id).order());
                config.layers.put(id, definition);
            } else {
                throw new IllegalArgumentException("Unknown layer: " + id);
            }
        }
        definition.displayName = displayName;
        if (!KeyLayers.isBuiltin(id)) {
            definition.order = clampOrder(order);
        }
        definition.normalize(id);
        return definition;
    }

    /**
     * 删除一个层级，并清掉配置里所有指向它的引用。
     *
     * @return 被一并清掉的引用条数（分类覆盖 + 单键覆盖），供界面提示
     * @throws IllegalArgumentException 试图删除内置层级
     */
    public static int delete(LayerConfig config, String id) {
        Objects.requireNonNull(config, "config");
        if (KeyLayers.isBuiltin(id)) {
            throw new IllegalArgumentException("Built-in layer cannot be deleted: " + id);
        }
        if (id == null || config.layers.remove(id) == null) {
            return 0;
        }
        return removeValue(config.categories, id) + removeValue(config.mappings, id);
    }

    /** 统计配置里有多少处引用指向该层级（不含单键覆盖里的分类部分）。 */
    public static int countReferences(LayerConfig config, String id) {
        if (config == null || id == null) {
            return 0;
        }
        return countValue(config.categories, id) + countValue(config.mappings, id);
    }

    // ------------------------------------------------------------ 内部

    private static int clampOrder(int order) {
        if (order < LayerDefinition.MIN_ORDER) {
            return LayerDefinition.MIN_ORDER;
        }
        return Math.min(order, LayerDefinition.MAX_ORDER);
    }

    private static int countValue(Map<String, String> map, String value) {
        int count = 0;
        for (String mapped : map.values()) {
            if (value.equals(mapped)) {
                count++;
            }
        }
        return count;
    }

    private static int removeValue(Map<String, String> map, String value) {
        int removed = 0;
        for (Iterator<Map.Entry<String, String>> it = map.entrySet().iterator(); it.hasNext(); ) {
            if (value.equals(it.next().getValue())) {
                it.remove();
                removed++;
            }
        }
        return removed;
    }
}
