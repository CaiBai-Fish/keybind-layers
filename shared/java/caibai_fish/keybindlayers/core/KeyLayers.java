package caibai_fish.keybindlayers.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内置层级表 + 层级注册表。
 *
 * <p>内置的 8 个层级按「现代游戏的常见操作层级」设计，从最底层（任何场合都在监听）
 * 到最上层（只在某个界面里生效）排列：</p>
 *
 * <pre>
 *   system     系统/全局：截图、视角、全屏、字幕等随时可用的开关
 *   debug      调试：F3 系列
 *   gameplay   游戏内操作：移动、跳跃、攻击、使用、丢出
 *   vehicle    载具/骑乘：原版没有，留给载具类 mod
 *   inventory  物品栏与容器：打开背包、丢弃、创造模式快捷栏
 *   social     社交：聊天、玩家列表、社交界面
 *   interface  界面：仅在 GUI 中生效的按键
 *   mods       未识别分类的兜底共享层（仅在 SHARED_LAYER 策略下使用）
 * </pre>
 *
 * <p>其它 mod 可以通过 {@link caibai_fish.keybindlayers.api.KeybindLayersApi#registerLayer}
 * 注册自己的层级（例如 {@code aircraft}）；整合包作者则通过配置里的
 * {@link LayerConfig#layers} 定义层级，由 {@link #applyDefinitions} 写入本注册表。</p>
 *
 * <p>内置层级的深度固定（不允许被配置覆盖），显示名可以改；非内置层级可以随意
 * 创建与删除，删除后引用它的分类 / 按键会回退到未知分类策略。</p>
 */
public final class KeyLayers {

    private static final Map<String, KeyLayer> REGISTERED = new ConcurrentHashMap<>();
    /** 由配置显式定义过的层级 id（不含内置），用于配置重载时清理已被删除的层级。 */
    private static final Set<String> CONFIG_DEFINED = ConcurrentHashMap.newKeySet();
    /** 层级的自定义显示名；没有条目时界面回退到语言文件 {@code keybindlayers.layer.<id>}。 */
    private static final Map<String, String> DISPLAY_NAMES = new ConcurrentHashMap<>();
    /** 内置层级 id：不允许删除，也不允许改深度。 */
    private static final Set<String> BUILTIN_IDS = new HashSet<>();

    /** 系统/全局层：F1~F12、截图、视角切换、字幕等。 */
    public static final KeyLayer SYSTEM = register(new KeyLayer("system", 0));
    /** 调试层：F3 系列调试键。 */
    public static final KeyLayer DEBUG = register(new KeyLayer("debug", 10));
    /** 游戏内操作层：移动、跳跃、攻击、使用物品、快捷栏。 */
    public static final KeyLayer GAMEPLAY = register(new KeyLayer("gameplay", 100));
    /** 载具/骑乘层：原版没有按键归入此层，供载具类 mod 声明。 */
    public static final KeyLayer VEHICLE = register(new KeyLayer("vehicle", 120));
    /** 物品栏/容器层：打开背包、丢弃物品、创造模式快捷栏保存。 */
    public static final KeyLayer INVENTORY = register(new KeyLayer("inventory", 150));
    /** 社交层：聊天、玩家列表、社交界面。 */
    public static final KeyLayer SOCIAL = register(new KeyLayer("social", 160));
    /** 界面层：只在 GUI 中生效的按键。 */
    public static final KeyLayer INTERFACE = register(new KeyLayer("interface", 200));
    /** 未识别分类的兜底共享层。 */
    public static final KeyLayer MODS = register(new KeyLayer("mods", 300));

    /**
     * 原版 7 个按键分类的内置默认分层。
     *
     * <p>按用户要求「简化标签栏」，原版<strong>只分两层</strong>：能在游戏内直接生效的
     * （移动、玩法，以及截图这类系统键）归 {@link #GAMEPLAY}；要打开或操作某个界面的
     * （物品栏、创造模式、多人/聊天、界面）归 {@link #INTERFACE}。需要区别对待的个别按键
     * 由 {@link DefaultAdaptations} 用单键覆盖处理。</p>
     *
     * <p>分类字符串就是 {@code KeyMapping#getCategory()} 的返回值（一个翻译键），
     * 在 1.20.1 与 1.21.1 中完全一致。mod 自己的分类不在这里，由
     * {@link UnknownCategoryPolicy} 决定如何归层。</p>
     */
    public static final Map<String, KeyLayer> DEFAULT_CATEGORY_LAYERS;

    static {
        Map<String, KeyLayer> defaults = new LinkedHashMap<>();
        defaults.put("key.categories.misc", GAMEPLAY);
        defaults.put("key.categories.ui", INTERFACE);
        defaults.put("key.categories.movement", GAMEPLAY);
        defaults.put("key.categories.gameplay", GAMEPLAY);
        defaults.put("key.categories.inventory", INTERFACE);
        defaults.put("key.categories.creative", INTERFACE);
        defaults.put("key.categories.multiplayer", INTERFACE);
        DEFAULT_CATEGORY_LAYERS = Collections.unmodifiableMap(defaults);

        Collections.addAll(BUILTIN_IDS, SYSTEM.id(), DEBUG.id(), GAMEPLAY.id(), VEHICLE.id(),
                INVENTORY.id(), SOCIAL.id(), INTERFACE.id(), MODS.id());
    }

    private KeyLayers() {
    }

    /**
     * 注册一个层级。若同 id 已存在则返回已注册的实例，保证层级的身份唯一。
     */
    public static KeyLayer register(KeyLayer layer) {
        return REGISTERED.computeIfAbsent(layer.id(), id -> layer);
    }

    /** 按 id 查找层级，不存在时返回 {@code null}。 */
    public static KeyLayer get(String id) {
        return REGISTERED.get(id);
    }

    /** 按 id 查找层级，不存在时抛异常，用于配置错误时快速失败。 */
    public static KeyLayer require(String id) {
        KeyLayer layer = REGISTERED.get(id);
        if (layer == null) {
            throw new IllegalArgumentException("Unknown keybind layer: '" + id + "'");
        }
        return layer;
    }

    /** 全部已注册层级，按深度升序。 */
    public static Collection<KeyLayer> all() {
        List<KeyLayer> layers = new ArrayList<>(REGISTERED.values());
        Collections.sort(layers);
        return Collections.unmodifiableList(layers);
    }

    /** 供 {@link caibai_fish.keybindlayers.core.LayerRules} 使用的内置默认分层。 */
    public static KeyLayer defaultLayerOfCategory(String category) {
        return DEFAULT_CATEGORY_LAYERS.get(category);
    }

    // ------------------------------------------------------------ 增删改

    /** 是否是内置层级（内置层级可以改显示名，但不能删除，深度也固定）。 */
    public static boolean isBuiltin(String id) {
        return id != null && BUILTIN_IDS.contains(id);
    }

    /**
     * 删除一个非内置层级。
     *
     * <p>只从注册表里摘掉；引用它的分类 / 按键由调用方通过
     * {@link LayerRules#removeReferencesTo(String)} 清理，清理后自动回退到未知分类策略。</p>
     *
     * @return 是否真的删除了（内置层级或不存在的层级返回 false）
     */
    public static boolean unregister(String id) {
        if (id == null || isBuiltin(id)) {
            return false;
        }
        boolean removed = REGISTERED.remove(id) != null;
        DISPLAY_NAMES.remove(id);
        CONFIG_DEFINED.remove(id);
        return removed;
    }

    /** 设置层级的自定义显示名；传 {@code null} 或空串表示回退到语言文件。 */
    public static void setDisplayName(String id, String displayName) {
        if (id == null || id.isEmpty()) {
            return;
        }
        if (displayName == null || displayName.trim().isEmpty()) {
            DISPLAY_NAMES.remove(id);
        } else {
            DISPLAY_NAMES.put(id, displayName.trim());
        }
    }

    /** 层级的自定义显示名；没有时为 {@code null}，界面应回退到 {@link KeyLayer#translationKey()}。 */
    public static String displayName(String id) {
        return id == null ? null : DISPLAY_NAMES.get(id);
    }

    /**
     * 用配置里的层级定义覆盖注册表。
     *
     * <p>语义：</p>
     * <ol>
     *   <li>内置层级：只接受显示名，深度一律保持内置值；</li>
     *   <li>非内置层级：按定义注册，深度改动立即生效；</li>
     *   <li>上一次由配置定义、这一次已从配置里删掉的层级一并从注册表移除，
     *       避免「界面上删了、判定时还在」。</li>
     * </ol>
     *
     * <p>通过 {@link caibai_fish.keybindlayers.api.KeybindLayersApi} 注册的层级不受影响——
     * 它们不属于配置定义的范围。</p>
     */
    public static void applyDefinitions(Map<String, LayerDefinition> definitions) {
        Set<String> desired = new HashSet<>();
        if (definitions != null) {
            for (Map.Entry<String, LayerDefinition> entry : definitions.entrySet()) {
                String id = entry.getKey();
                LayerDefinition definition = entry.getValue();
                if (id == null || id.isEmpty() || definition == null) {
                    continue;
                }
                definition.normalize(id);
                desired.add(id);
                CONFIG_DEFINED.add(id);
                if (isBuiltin(id)) {
                    setDisplayName(id, definition.displayName);
                } else {
                    REGISTERED.put(id, new KeyLayer(id, definition.order));
                    setDisplayName(id, definition.displayName);
                }
            }
        }
        for (String id : new ArrayList<>(CONFIG_DEFINED)) {
            if (!desired.contains(id) && !isBuiltin(id)) {
                REGISTERED.remove(id);
                DISPLAY_NAMES.remove(id);
                CONFIG_DEFINED.remove(id);
            }
        }
    }

    /** 当前由配置定义的层级 id（不含内置），供管理界面列出「可删除的层级」。 */
    public static Set<String> configDefinedIds() {
        return Collections.unmodifiableSet(new HashSet<>(CONFIG_DEFINED));
    }
}
