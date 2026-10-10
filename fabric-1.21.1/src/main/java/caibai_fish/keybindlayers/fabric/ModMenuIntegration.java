package caibai_fish.keybindlayers.fabric;

import caibai_fish.keybindlayers.client.LayerManagerScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * 「模组列表 → 配置」入口（需要装 ModMenu）。
 *
 * <p>ModMenu 是<strong>可选依赖</strong>：本类会引用它的 API，因此没有 ModMenu 时
 * 不能加载它。Fabric Loader 只在 ModMenu 存在时才会去取 {@code modmenu} 入口点，
 * 所以没装 ModMenu 的玩家根本不会触发这个类的加载，也就不会
 * {@code NoClassDefFoundError}。</p>
 *
 * <p>界面本身（{@link LayerManagerScreen}）来自共享源码，与 NeoForge 侧是同一份；
 * 差别只在「怎么把它挂到模组列表上」——那边用 {@code IConfigScreenFactory}。</p>
 */
public final class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return LayerManagerScreen::new;
    }
}
