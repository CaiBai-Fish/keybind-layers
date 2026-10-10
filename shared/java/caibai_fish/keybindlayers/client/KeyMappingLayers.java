package caibai_fish.keybindlayers.client;

import caibai_fish.keybindlayers.core.KeyLayer;
import caibai_fish.keybindlayers.core.KeyLayers;
import caibai_fish.keybindlayers.core.LayerRules;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.KeyMapping;

/**
 * {@link KeyMapping} 与层级模型之间的桥接。
 *
 * <p>本文件依赖 Minecraft 类，但只用到 {@code getCategory()}、{@code getName()}、
 * {@code same()} 这几个在 1.20.1 与 1.21.1 中同名同签名的方法
 * （已用 javap 核实：{@code getCategory()Ljava/lang/String;}），
 * 因此各加载器工程共用这一份实现。</p>
 *
 * <p>一切加载器特有的概念——修饰键（{@link KeyModifiers}）与按键自己声明的作用域
 * （{@link KeyContexts}）——都走注入，由平台入口在客户端初始化时装上。
 * <strong>Fabric 两个都不装</strong>，于是自动退回「原版判定 + 配置与未知分类策略」。</p>
 */
public final class KeyMappingLayers {

    /**
     * 「自定义作用域层」的前缀。
     *
     * <p>mod 自己实现的 {@code IKeyConflictContext} 读不出 {@code IN_GAME} / {@code GUI}，
     * 但它本身就是作者写下的作用域声明。于是拿它的类型名当一个专属层：同一个界面的按键
     * 同层（彼此仍会报冲突），不同 mod 的界面天然不同层——它们的界面永远不会同时打开，
     * 本来就不该互相报冲突。</p>
     */
    public static final String CONTEXT_LAYER_PREFIX = "context:";

    /** 自定义作用域层的深度，排在 {@code interface}(200) 之后；只影响排序，不参与判定。 */
    private static final int CONTEXT_LAYER_ORDER = 250;

    private KeyMappingLayers() {
    }

    /**
     * 某个按键绑定所属的操作层级。
     *
     * <p>解析顺序：</p>
     * <ol>
     *   <li><b>配置里的显式归属</b>——按键级或分类级都算，内置适配表也在其中；</li>
     *   <li><b>按键自己声明的作用域</b>（{@link KeyContexts}）：{@code IN_GAME} → {@code gameplay}、
     *       {@code GUI} → {@code interface}；自定义 context 则按它的类型即时成层
     *       （见 {@link #CONTEXT_LAYER_PREFIX}）。仅在配置没写过、且
     *       {@link LayerRules#useDeclaredKeyContext()} 开启时生效——mod 作者自己写的声明
     *       比我们猜得准；</li>
     *   <li>内置原版分类与未知分类策略（{@link LayerRules#layerOf}）。</li>
     * </ol>
     */
    public static KeyLayer layerOf(KeyMapping mapping) {
        String category = mapping.getCategory();
        String name = mapping.getName();
        if (LayerRules.useDeclaredKeyContext() && !LayerRules.hasExplicitAssignment(category, name)) {
            String declaredLayerId = KeyContexts.layerIdOf(mapping);
            if (declaredLayerId != null) {
                return declaredLayer(declaredLayerId);
            }
        }
        return LayerRules.layerOf(category, name);
    }

    /**
     * 取声明出来的层级；自定义作用域层第一次遇到时即时注册，并给一个人看得懂的显示名。
     */
    private static KeyLayer declaredLayer(String layerId) {
        KeyLayer layer = KeyLayers.get(layerId);
        if (layer != null) {
            return layer;
        }
        layer = KeyLayers.register(new KeyLayer(layerId, CONTEXT_LAYER_ORDER));
        // 平台侧已经拿到 mod 显示名时不要覆盖它，这里只是兜底。
        if (layerId.startsWith(CONTEXT_LAYER_PREFIX) && KeyLayers.displayName(layerId) == null) {
            KeyLayers.setDisplayName(layerId, contextDisplayName(layerId));
        }
        return layer;
    }

    /** {@code context:mezz.jei.neoforge.input.JeiForgeKeyConflictContexts} → {@code JeiForgeKeyConflictContexts}。 */
    private static String contextDisplayName(String layerId) {
        String rest = layerId.substring(CONTEXT_LAYER_PREFIX.length());
        int dot = rest.lastIndexOf('.');
        return dot >= 0 && dot + 1 < rest.length() ? rest.substring(dot + 1) : rest;
    }

    /** 两个按键绑定是否属于同一操作层级。 */
    public static boolean sameLayer(KeyMapping a, KeyMapping b) {
        return LayerRules.sameLayer(a.getCategory(), a.getName(), b.getCategory(), b.getName());
    }

    /**
     * 原版已经判定为冲突的两个绑定，是否仍然应该显示成冲突。
     *
     * <p>Mixin 在 {@code KeyBindsList$KeyEntry#refreshEntry} 里把它与
     * {@code KeyMapping#same} / {@code KeyMapping#hasKeyModifierConflict} 的结果相与。</p>
     *
     * <p>这里要回答两个问题，都为真才算冲突：</p>
     * <ol>
     *   <li><b>修饰键相同</b>（仅在 {@link LayerRules#separateModifierCombinations()} 开启时检查）：
     *       原版把 {@code W} 与 {@code Shift+W} 判为冲突，但按 W 并不会触发 {@code Shift+W}，
     *       玩家把两者绑到不同功能通常是有意的组合，不该标红；</li>
     *   <li><b>属于同一操作层级</b>：{@link LayerRules#reportConflict}。</li>
     * </ol>
     */
    public static boolean reportConflict(KeyMapping a, KeyMapping b) {
        if (LayerRules.separateModifierCombinations() && !KeyModifiers.sameModifier(a, b)) {
            return false;
        }
        return LayerRules.reportConflict(a.getCategory(), a.getName(), b.getCategory(), b.getName());
    }

    /**
     * 找出与给定绑定真正冲突的其它绑定，即「同一个键、同一个修饰键 <em>且</em> 属于同一层级」的那些。
     *
     * <p>判据与原版 {@code KeyBindsList$KeyEntry#refreshEntry} 一致（{@code same}，
     * Forge / NeoForge 上再加上 {@code hasKeyModifierConflict}——经
     * {@link KeyModifiers#hasModifierConflict} 间接调用，Fabric 上该判据恒为假），
     * 只是额外要求同一修饰键与同一层级；自建界面用它决定是否标红、以及生成 tooltip。</p>
     *
     * <p>原版对未绑定的按键不做冲突检测，这里同样直接返回空列表。</p>
     */
    public static List<KeyMapping> conflictsOf(KeyMapping mapping, KeyMapping[] all) {
        List<KeyMapping> conflicts = new ArrayList<>();
        if (mapping == null || all == null || mapping.isUnbound()) {
            return conflicts;
        }
        for (KeyMapping other : all) {
            if (other == null || other == mapping) {
                continue;
            }
            if ((mapping.same(other) || KeyModifiers.hasModifierConflict(other, mapping))
                    && reportConflict(mapping, other)) {
                conflicts.add(other);
            }
        }
        return conflicts;
    }

    /**
     * 把一组按键按操作层级分组，层级按深度升序排列，层内保持传入顺序。
     *
     * <p>自建界面的标签栏与列表分组都用它；返回的是可变的有序 Map。</p>
     */
    public static Map<KeyLayer, List<KeyMapping>> groupByLayer(KeyMapping[] mappings) {
        Map<KeyLayer, List<KeyMapping>> grouped = new LinkedHashMap<>();
        if (mappings != null) {
            for (KeyMapping mapping : mappings) {
                if (mapping == null) {
                    continue;
                }
                grouped.computeIfAbsent(layerOf(mapping), layer -> new ArrayList<>()).add(mapping);
            }
        }
        List<KeyLayer> ordered = new ArrayList<>(grouped.keySet());
        Collections.sort(ordered);
        Map<KeyLayer, List<KeyMapping>> sorted = new LinkedHashMap<>();
        for (KeyLayer layer : ordered) {
            sorted.put(layer, grouped.get(layer));
        }
        return sorted;
    }
}
