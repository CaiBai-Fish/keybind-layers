package caibai_fish.keybindlayers.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code shared/core} 的纯逻辑测试：层级增删改、引用回退、配置来源与 API 来源的隔离。
 *
 * <p>这些类不依赖任何 Minecraft 类型，所以测试不需要启动游戏，直接 {@code gradlew test} 即可。
 * 这批断言原本是一次性验证程序，现固化下来防止回归。</p>
 */
class LayerCoreTest {

    @BeforeEach
    void resetState() {
        LayerRules.clearAssignments();
        LayerRules.setEnabled(true);
        LayerRules.setSeparateModifierCombinations(true);
        LayerRules.setUseDeclaredKeyContext(true);
        LayerRules.setUnknownCategoryPolicy(UnknownCategoryPolicy.OWN_LAYER);
        // 上一次测试可能注册过这些层级。
        KeyLayers.unregister("aircraft");
        KeyLayers.unregister("tmp_layer");
    }

    // ------------------------------------------------------------ 内置层级

    @Test
    @DisplayName("内置层级不可删除，且始终留在注册表里")
    void builtinLayersAreProtected() {
        assertTrue(KeyLayers.isBuiltin("gameplay"));
        assertFalse(KeyLayers.isBuiltin("aircraft"));
        assertFalse(KeyLayers.unregister("gameplay"));
        assertNotNull(KeyLayers.get("gameplay"));
    }

    // ------------------------------------------------------------ id 校验

    @Test
    @DisplayName("层级 id：小写字母开头，只含小写字母、数字与下划线")
    void layerIdValidation() {
        assertTrue(LayerManager.isValidId("aircraft"));
        assertTrue(LayerManager.isValidId("a_1"));
        assertFalse(LayerManager.isValidId("Aircraft"));
        assertFalse(LayerManager.isValidId("1aircraft"));
        assertFalse(LayerManager.isValidId("air-craft"));
        assertFalse(LayerManager.isValidId(""));
        assertFalse(LayerManager.isValidId(null));
    }

    // ------------------------------------------------------------ 创建

    @Test
    @DisplayName("创建层级：规范化 id、写入显示名与深度，并拒绝非法/重复/内置 id")
    void createLayer() {
        LayerConfig config = LayerConfig.defaults();
        LayerDefinition created = LayerManager.create(config, "Aircraft", "飞行器", 130);

        assertEquals("aircraft", created.id);
        assertEquals("飞行器", config.layers.get("aircraft").displayName);
        assertEquals(130, config.layers.get("aircraft").order);

        assertThrows(IllegalArgumentException.class,
                () -> LayerManager.create(config, "aircraft", null, 100), "重复 id 应被拒绝");
        assertThrows(IllegalArgumentException.class,
                () -> LayerManager.create(config, "gameplay", null, 100), "与内置同 id 应被拒绝");
        assertThrows(IllegalArgumentException.class,
                () -> LayerManager.create(config, "bad-id", null, 100), "非法 id 应被拒绝");
    }

    // ------------------------------------------------------------ 应用定义

    @Test
    @DisplayName("applyDefinitions：注册自定义层级，内置层级的深度不可被配置覆盖")
    void applyDefinitions() {
        LayerConfig config = LayerConfig.defaults();
        LayerManager.create(config, "aircraft", "飞行器", 130);
        KeyLayers.applyDefinitions(config.layers);

        KeyLayer aircraft = KeyLayers.get("aircraft");
        assertNotNull(aircraft);
        assertEquals(130, aircraft.order());
        assertEquals("飞行器", KeyLayers.displayName("aircraft"));
        assertTrue(KeyLayers.configDefinedIds().contains("aircraft"));

        // 内置层级：显示名可改，深度以代码里的为准。
        config.layers.put("gameplay", new LayerDefinition("gameplay", "玩法", 999));
        KeyLayers.applyDefinitions(config.layers);
        assertEquals(KeyLayers.GAMEPLAY.order(), KeyLayers.require("gameplay").order());
        assertEquals("玩法", KeyLayers.displayName("gameplay"));
    }

    @Test
    @DisplayName("从配置里删掉的层级会同步从注册表移除")
    void removedLayersAreUnregistered() {
        LayerConfig config = LayerConfig.defaults();
        LayerManager.create(config, "tmp_layer", null, 200);
        KeyLayers.applyDefinitions(config.layers);
        assertNotNull(KeyLayers.get("tmp_layer"));

        LayerManager.delete(config, "tmp_layer");
        KeyLayers.applyDefinitions(config.layers);
        assertNull(KeyLayers.get("tmp_layer"));
    }

    // ------------------------------------------------------------ 引用回退

    @Test
    @DisplayName("删除层级会清掉指向它的引用，且不影响其它层级")
    void deleteCleansReferences() {
        LayerConfig config = LayerConfig.defaults();
        LayerManager.create(config, "aircraft", null, 130);
        config.categories.put("key.categories.aircraftmod", "aircraft");
        config.mappings.put("key.categories.aircraftmod|key.aircraftmod.gear", "aircraft");
        config.categories.put("key.categories.other", "vehicle");

        assertEquals(2, LayerManager.countReferences(config, "aircraft"));
        assertEquals(2, LayerManager.delete(config, "aircraft"));

        assertFalse(config.layers.containsKey("aircraft"));
        assertFalse(config.categories.containsValue("aircraft"));
        assertFalse(config.mappings.containsValue("aircraft"));
        assertEquals("vehicle", config.categories.get("key.categories.other"));
        assertThrows(IllegalArgumentException.class, () -> LayerManager.delete(config, "gameplay"),
                "内置层级不该被删掉");
    }

    // ------------------------------------------------------------ 配置来源 vs API 来源

    @Test
    @DisplayName("清除配置赋值时必须保留其它 mod 通过 API 声明的归属")
    void configAndApiAssignmentsAreSeparate() {
        LayerRules.assignCategory("key.categories.api", "api_layer");
        LayerRules.assignCategoryFromConfig("key.categories.cfg", "cfg_layer");
        assertEquals("api_layer", LayerRules.layerIdOfCategory("key.categories.api"));
        assertEquals("cfg_layer", LayerRules.layerIdOfCategory("key.categories.cfg"));

        LayerRules.clearConfigAssignments();

        assertEquals("api_layer", LayerRules.layerIdOfCategory("key.categories.api"),
                "API 来源的赋值不该被配置重载清掉");
        assertNotEquals("cfg_layer", LayerRules.layerIdOfCategory("key.categories.cfg"));
    }

    @Test
    @DisplayName("removeReferencesTo 之后分类回退到 own_layer 临时层")
    void removeReferencesTo() {
        LayerRules.assignCategoryFromConfig("key.categories.aircraftmod", "aircraft");
        LayerRules.assignMappingFromConfig("key.categories.aircraftmod", "key.aircraftmod.gear", "aircraft");
        assertEquals(2, LayerRules.countReferencesTo("aircraft"));

        assertEquals(2, LayerRules.removeReferencesTo("aircraft"));
        assertEquals(0, LayerRules.countReferencesTo("aircraft"));
        assertTrue(LayerRules.layerIdOfCategory("key.categories.aircraftmod")
                .startsWith(LayerRules.OWN_LAYER_PREFIX));
    }

    // ------------------------------------------------------------ 修改

    @Test
    @DisplayName("update：内置层级可改显示名、深度保持内置值；未知层级抛异常")
    void updateLayer() {
        LayerConfig config = LayerConfig.defaults();
        LayerDefinition updated = LayerManager.update(config, "gameplay", "玩法", 5);

        assertEquals("玩法", updated.displayName);
        assertEquals(KeyLayers.require("gameplay").order(), updated.order);
        assertThrows(IllegalArgumentException.class,
                () -> LayerManager.update(config, "not_exists", null, 100));
    }

    // ------------------------------------------------------------ 默认适配

    @Test
    @DisplayName("原版只分两层：能在游戏内直接生效的归 gameplay，要开界面的归 interface")
    void vanillaOnlyTwoLayers() {
        LayerConfig config = LayerConfig.defaults();

        assertEquals("gameplay", config.categories.get("key.categories.movement"));
        assertEquals("gameplay", config.categories.get("key.categories.gameplay"));
        assertEquals("gameplay", config.categories.get("key.categories.misc"));
        assertEquals("interface", config.categories.get("key.categories.ui"));
        assertEquals("interface", config.categories.get("key.categories.inventory"));
        assertEquals("interface", config.categories.get("key.categories.creative"));
        assertEquals("interface", config.categories.get("key.categories.multiplayer"));

        // 与所属分类不一致的少数按键靠单键覆盖。
        assertEquals("interface", config.mappings.get("key.categories.misc|key.advancements"));
        assertEquals("gameplay", config.mappings.get("key.categories.inventory|key.drop"));
        assertEquals("gameplay", config.mappings.get("key.categories.inventory|key.swapOffhand"));

        // 内置层级本身仍然存在（只是没有按键，不会出现在标签栏里）。
        assertTrue(KeyLayers.isBuiltin("system"));
        assertTrue(KeyLayers.isBuiltin("inventory"));
        assertTrue(KeyLayers.isBuiltin("social"));
    }

    @Test
    @DisplayName("默认适配表：读不出作用域的 mod 用分类级手工条目")
    void modAdaptations() {
        LayerConfig config = LayerConfig.defaults();

        assertEquals("gameplay", config.categories.get("jm.common.hotkeys_keybinding_category"));
        assertEquals("interface", config.categories.get("jm.common.hotkeys_keybinding_fullscreen_category"));
        assertEquals("gameplay", config.categories.get("Xaero's Minimap"));
        assertEquals("interface", config.categories.get("Xaero's World Map"));
        // JEI 现在交给自定义作用域自动成层，不再手工写。
        assertFalse(config.categories.containsKey("jei.key.category.overlays"));
    }

    // ------------------------------------------------------------ 判定开关

    @Test
    @DisplayName("enabled=false 时完全退回原版的冲突判定")
    void disabledFallsBackToVanilla() {
        LayerRules.setEnabled(false);
        assertTrue(LayerRules.reportConflict("key.categories.misc", "key.screenshot",
                "key.categories.movement", "key.jump"));
    }

    @Test
    @DisplayName("只有同层级才报冲突")
    void onlySameLayerConflicts() {
        assertTrue(LayerRules.reportConflict("key.categories.movement", "key.jump",
                "key.categories.gameplay", "key.attack"), "同属 gameplay");
        assertFalse(LayerRules.reportConflict("key.categories.misc", "key.screenshot",
                "key.categories.ui", "key.socialInteractions"), "gameplay 与 interface 不同层");
    }

    @Test
    @DisplayName("hasExplicitAssignment 认得按键级与分类级两种显式归属")
    void explicitAssignmentDetection() {
        LayerRules.assignCategoryFromConfig("key.categories.misc", "system");
        assertTrue(LayerRules.hasExplicitAssignment("key.categories.misc", "key.screenshot"));
        assertFalse(LayerRules.hasExplicitAssignment("key.categories.movement", "key.jump"));

        LayerRules.assignMappingFromConfig("key.categories.movement", "key.jump", "gameplay");
        assertTrue(LayerRules.hasExplicitAssignment("key.categories.movement", "key.jump"));
    }

    // ------------------------------------------------------------ 锁定的按键

    @Test
    @DisplayName("锁定的按键：快捷栏 1-9 与全屏")
    void lockedKeybinds() {
        assertTrue(LockedKeybinds.isLocked("key.hotbar.1"));
        assertTrue(LockedKeybinds.isLocked("key.hotbar.9"));
        assertTrue(LockedKeybinds.isLocked("key.fullscreen"));
        assertFalse(LockedKeybinds.isLocked("key.jump"));
        assertFalse(LockedKeybinds.isLocked("key.screenshot"));
        assertFalse(LockedKeybinds.isLocked(null));
    }
}
