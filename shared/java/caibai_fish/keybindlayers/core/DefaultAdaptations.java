package caibai_fish.keybindlayers.core;

/**
 * 内置的默认分层适配：原版按键的按用途细分，以及读不出作用域、只能手工写的 mod 分类。
 *
 * <p>这些内容只会写进<strong>新生成</strong>的 {@code config/keybindlayers.json}
 * （见 {@link LayerConfig#defaults()}）。已有配置不会被自动改写——玩家可以随意删改这些条目，
 * 删掉就退回 {@link UnknownCategoryPolicy} 的兜底规则。</p>
 *
 * <h2>原版只分两层</h2>
 * <p>按用户要求「简化标签栏」，原版 7 个分类被压成 {@code gameplay} 与 {@code interface}
 * 两层（见 {@link KeyLayers#DEFAULT_CATEGORY_LAYERS}）：能在游戏内直接生效的归前者，
 * 要打开/操作界面的归后者。这里只补上少数与所属分类不一致的按键——原版按键全部是
 * {@code UNIVERSAL} 作用域，没有自我声明，只能手工写。</p>
 *
 * <h2>为什么 mod 只剩这两家</h2>
 * <p>1.21.1 起可以读按键声明的 {@code IKeyConflictContext} 来自动分层，实测
 * （2026-10-10，装 JEI / JourneyMap / Mekanism / Xaero's）有三种情况：</p>
 * <ul>
 *   <li><b>标准声明</b>（{@code IN_GAME} / {@code GUI}）：Mekanism 整组都是，直接自动判定。
 *       而且它比手工更准——{@code key.mekanism.module_tweaker} 原本被这里误判成
 *       {@code interface}，实际声明是 {@code IN_GAME}。</li>
 *   <li><b>自定义 context</b>：JEI 用的是自己的 {@code JeiForgeKeyConflictContexts}，
 *       判断的是「当前屏幕是不是 JEI 界面」。这类现在也<strong>自动处理</strong>了：
 *       拿 context 的类型名当一个专属层（见
 *       {@code client/KeyMappingLayers#CONTEXT_LAYER_PREFIX}），于是同一个界面的键同层、
 *       不同 mod 的界面天然分开。把它们压成同一个 {@code interface} 反而会制造假冲突
 *       ——不同 mod 的界面永远不会同时打开。</li>
 *   <li><b>没有声明</b>（{@code UNIVERSAL}，即默认值）：JourneyMap 的分类里混着这种按键，
 *       Xaero's 更是 30 个键全是。只靠自动判定会把同一组拆到两个层级，所以这两家留在这里。</li>
 * </ul>
 *
 * <h2>数据来源</h2>
 * <p>下面的分类字符串与按键名都来自实际加载 mod 后打印的 {@code KeyMapping#getName()} /
 * {@code #getCategory()}，不是从语言文件推测的——这些 mod 的命名毫无规范：Xaero's 的分类
 * 直接是人可读的 {@code Xaero's Minimap}，按键名前缀还是 {@code gui.xaero_*} 而不是
 * {@code key.*}；JourneyMap 用的是 {@code jm.common.hotkeys_keybinding_category}。</p>
 */
public final class DefaultAdaptations {

    private DefaultAdaptations() {
    }

    /** 把默认适配写进一份配置对象。 */
    public static void applyTo(LayerConfig config) {
        if (config == null) {
            return;
        }
        applyVanillaRefinements(config);
        applyJourneyMap(config);
        applyXaero(config);
    }

    // ------------------------------------------------------------ 原版

    /**
     * 原版分类只分两层（见 {@link KeyLayers#DEFAULT_CATEGORY_LAYERS}），这里补上少数
     * 与所属分类不一致的按键。
     *
     * <p>{@code misc} 整体归游戏内，但「进度」打开的是一个 GUI；{@code inventory} 整体
     * 归界面，但丢出物品与交换副手是游戏内随时可用的操作。这两个分类里的其余按键
     * （截图、打开背包、保存/加载快捷栏）跟着分类走就够了。</p>
     */
    private static void applyVanillaRefinements(LayerConfig config) {
        config.mappings.put("key.categories.misc|key.advancements", "interface");
        config.mappings.put("key.categories.inventory|key.drop", "gameplay");
        config.mappings.put("key.categories.inventory|key.swapOffhand", "gameplay");
    }

    // ------------------------------------------------------------ JourneyMap

    /**
     * JourneyMap：小地图快捷键游戏内生效；全屏地图那一组只在全屏地图里生效。
     *
     * <p>这两组内部都混着 {@code UNIVERSAL}（没有声明）的按键，若只依赖自动判定
     * 会把同一组拆到两个层级，所以整组写死。</p>
     */
    private static void applyJourneyMap(LayerConfig config) {
        config.categories.put("jm.common.hotkeys_keybinding_category", "gameplay");
        config.categories.put("jm.common.hotkeys_keybinding_fullscreen_category", "interface");
    }

    // ------------------------------------------------------------ Xaero's

    /**
     * Xaero's：按键名前缀是 {@code gui.xaero_*} 而不是 {@code key.*}，分类直接用人可读名字，
     * 而且全部声明为 {@code UNIVERSAL}（没有作用域信息），所以只能手工适配。
     *
     * <p>小地图那一组绝大多数是游戏内随时可用的开关（缩放、切换小地图、路径点），
     * 但「打开设置 / 服务器配置 / 路径点列表」是打开界面，单独挪到 {@code interface}。
     * 世界地图那一组全部只在全屏地图界面里生效。</p>
     */
    private static void applyXaero(LayerConfig config) {
        config.categories.put("Xaero's Minimap", "gameplay");
        config.mappings.put("Xaero's Minimap|gui.xaero_minimap_settings", "interface");
        config.mappings.put("Xaero's Minimap|gui.xaero_minimap_server_profiles", "interface");
        config.mappings.put("Xaero's Minimap|gui.xaero_waypoints_key", "interface");

        config.categories.put("Xaero's World Map", "interface");
    }
}
