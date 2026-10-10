package caibai_fish.keybindlayers.client;

import java.util.function.Function;

/**
 * 由 mod id 查它的显示名。
 *
 * <h2>为什么是注入式</h2>
 * <p>各加载器的查询方式毫无共同点：NeoForge / Forge 是
 * {@code ModList.get().getModContainerById(id).get().getModInfo().getDisplayName()}，
 * Fabric 是 {@code FabricLoader.getInstance().getModContainer(id)} 再取
 * {@code getMetadata().getName()}，而本文件位于各工程共用的 {@code shared/}，
 * 不能 import 任何一方。</p>
 *
 * <p>未注入或查不到时返回 {@code null}，调用方会退回类简单名。</p>
 */
public final class ModNames {

    /** 平台注入的转换器：mod id → 显示名；返回 {@code null} 表示查不到。 */
    private static volatile Function<String, String> resolver;

    private ModNames() {
    }

    /** 由平台入口注入；传 {@code null} 可撤销。 */
    public static void install(Function<String, String> modNameResolver) {
        resolver = modNameResolver;
    }

    /** mod id 对应的显示名；未注入或该 id 不是已加载的 mod 时返回 {@code null}。 */
    public static String displayNameOf(String modId) {
        Function<String, String> current = resolver;
        if (current == null || modId == null || modId.isEmpty()) {
            return null;
        }
        try {
            return current.apply(modId);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
