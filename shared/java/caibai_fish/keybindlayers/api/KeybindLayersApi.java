package caibai_fish.keybindlayers.api;

import caibai_fish.keybindlayers.core.KeyLayer;
import caibai_fish.keybindlayers.core.KeyLayers;
import caibai_fish.keybindlayers.core.LayerRules;
import caibai_fish.keybindlayers.core.UnknownCategoryPolicy;

/**
 * 提供给其它 mod 的公开入口。
 *
 * <h2>使用场景</h2>
 * <p>默认情况下，本 mod 已经能消除「原版分类之间」以及「不同分类的 mod 按键之间」的
 * 假冲突（见 {@link UnknownCategoryPolicy#OWN_LAYER}）。当一个 mod 的按键都注册在同一个
 * 分类下、但其中一部分只在特定场合生效时（例如飞行器的「起落架」只在载具里可用），
 * 它可以通过本 API 把那些按键单独挪到自己的层级：</p>
 *
 * <pre>{@code
 * // 在自己的客户端初始化里调用（注册按键之后即可）
 * KeybindLayersApi.assignMapping("key.categories.myvehicles", "key.myvehicles.landing_gear", "vehicle");
 * }</pre>
 *
 * <h2>稳定性</h2>
 * <p>所有方法线程安全，可在任意时机调用；按键注册阶段调用最合适。
 * 运行期调用也会立即影响「控制」设置界面下一次重绘的冲突提示。</p>
 *
 * <h2>软依赖</h2>
 * <p>本类是纯 Java（不含 Minecraft 类型），因此可以在没有安装本 mod 时安全地
 * 捕获 {@link NoClassDefFoundError} 来降级，或直接用
 * {@code ModList.get().isLoaded("keybindlayers")} 判断后再调用。</p>
 */
public final class KeybindLayersApi {

    public static final String MOD_ID = "keybindlayers";

    private KeybindLayersApi() {
    }

    // ------------------------------------------------------------------ 层级

    /**
     * 注册一个新层级（例如 {@code "aircraft"}）。同 id 重复注册返回已存在的实例。
     *
     * @param id    小写字母、数字与下划线，会用于生成翻译键 {@code keybindlayers.layer.<id>}
     * @param order 深度，数值越小越底层；仅影响排序展示。内置层级的取值见 {@link KeyLayers}
     */
    public static KeyLayer registerLayer(String id, int order) {
        return KeyLayers.register(new KeyLayer(id, order));
    }

    /** 按 id 查找已注册层级，不存在时返回 {@code null}。 */
    public static KeyLayer layer(String id) {
        return KeyLayers.get(id);
    }

    // ------------------------------------------------------------------ 赋值

    /** 把一个按键分类整体归入某个已注册层级。 */
    public static void assignCategory(String category, KeyLayer layer) {
        if (layer != null) {
            LayerRules.assignCategory(category, layer.id());
        }
    }

    /** 把一个按键分类整体归入某个层级 id（层级不存在时会按该 id 即时成层）。 */
    public static void assignCategory(String category, String layerId) {
        LayerRules.assignCategory(category, layerId);
    }

    /**
     * 把某个分类下的单个按键归入某个层级，优先级高于分类级设置。
     *
     * @param category    按键分类，即 {@code KeyMapping#getCategory()} 的值
     * @param mappingName 按键名，即 {@code KeyMapping#getName()} 的值，例如 {@code key.jump}
     */
    public static void assignMapping(String category, String mappingName, KeyLayer layer) {
        if (layer != null) {
            LayerRules.assignMapping(category, mappingName, layer.id());
        }
    }

    /** 同上，按层级 id 指定。 */
    public static void assignMapping(String category, String mappingName, String layerId) {
        LayerRules.assignMapping(category, mappingName, layerId);
    }

    // ------------------------------------------------------------------ 查询

    /** 查询某个分类当前所属的层级。 */
    public static KeyLayer layerOfCategory(String category) {
        return LayerRules.layerOfCategory(category);
    }

    /** 查询某个具体按键当前所属的层级（含单键覆盖）。 */
    public static KeyLayer layerOf(String category, String mappingName) {
        return LayerRules.layerOf(category, mappingName);
    }

    /** 两个具体按键是否属于同一操作层级。 */
    public static boolean sameLayer(String categoryA, String mappingNameA,
                                    String categoryB, String mappingNameB) {
        return LayerRules.sameLayer(categoryA, mappingNameA, categoryB, mappingNameB);
    }
}
