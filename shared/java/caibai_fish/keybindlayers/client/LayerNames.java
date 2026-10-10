package caibai_fish.keybindlayers.client;

import caibai_fish.keybindlayers.core.KeyLayer;
import caibai_fish.keybindlayers.core.KeyLayers;
import caibai_fish.keybindlayers.core.LayerRules;
import net.minecraft.network.chat.Component;

/**
 * 层级显示名的解析（游戏内文本）。
 *
 * <p>解析顺序：</p>
 * <ol>
 *   <li>配置里的自定义显示名（整合包作者可以直接写中文，不必附带资源包）；</li>
 *   <li>{@code category:xxx} 形式的临时层——取 {@code xxx} 作为分类翻译键，
 *       显示分类自身的本地化名（见 {@link LayerRules#OWN_LAYER_PREFIX}）；</li>
 *   <li>语言文件 {@code keybindlayers.layer.<id>}；</li>
 *   <li>都没有时 Minecraft 会原样显示该翻译键，等于回退到 id。</li>
 * </ol>
 */
public final class LayerNames {

    private LayerNames() {
    }

    /** 层级的可显示文本。 */
    public static Component of(KeyLayer layer) {
        if (layer == null) {
            return Component.empty();
        }
        String custom = KeyLayers.displayName(layer.id());
        if (custom != null) {
            return Component.literal(custom);
        }
        String id = layer.id();
        if (id.startsWith(LayerRules.OWN_LAYER_PREFIX)) {
            String category = id.substring(LayerRules.OWN_LAYER_PREFIX.length());
            if (!category.isEmpty()) {
                return Component.translatable(category);
            }
        }
        return Component.translatable(layer.translationKey());
    }

    /** 按 id 直接取显示名，供只有层级 id 的场合（例如配置界面列表）使用。 */
    public static Component ofId(String layerId) {
        if (layerId == null || layerId.isEmpty()) {
            return Component.empty();
        }
        KeyLayer layer = KeyLayers.get(layerId);
        if (layer != null) {
            return of(layer);
        }
        String custom = KeyLayers.displayName(layerId);
        if (custom != null) {
            return Component.literal(custom);
        }
        return Component.translatable("keybindlayers.layer." + layerId);
    }
}
