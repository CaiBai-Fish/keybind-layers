package caibai_fish.keybindlayers.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 配置读写测试。重点：写出来的文件带注释，而读回来必须完全一致
 * ——注释是靠 Gson {@code JsonReader} 的宽容模式跳过的。
 */
class LayerConfigIOTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void resetState() {
        LayerRules.clearAssignments();
        KeyLayers.unregister("aircraft");
    }

    @Test
    @DisplayName("写出的配置带注释，读回来内容一致（JSONC 往返）")
    void jsoncRoundTrip() throws IOException {
        LayerConfig original = LayerConfig.defaults();
        LayerManager.create(original, "aircraft", "飞行器", 130);
        original.packAuthorMode = true;
        original.logLayerDecisions = true;
        original.separateModifierCombinations = false;

        LayerConfigIO.save(tempDir, original);

        String text = Files.readString(LayerConfigIO.configPath(tempDir));
        assertTrue(text.contains("//"), "写出的配置应当带注释");
        assertTrue(text.contains("unknownCategoryPolicy"), "字段名应当保持原样");

        LayerConfig loaded = LayerConfigIO.load(tempDir);

        assertEquals(original.enabled, loaded.enabled);
        assertEquals(original.packAuthorMode, loaded.packAuthorMode);
        assertEquals(original.logLayerDecisions, loaded.logLayerDecisions);
        assertEquals(original.separateModifierCombinations, loaded.separateModifierCombinations);
        assertEquals(original.useDeclaredKeyContext, loaded.useDeclaredKeyContext);
        assertEquals(original.unknownCategoryPolicy, loaded.unknownCategoryPolicy);
        assertEquals(original.categories, loaded.categories);
        assertEquals(original.mappings, loaded.mappings);

        assertEquals(original.layers.keySet(), loaded.layers.keySet());
        LayerDefinition aircraft = loaded.layers.get("aircraft");
        assertNotNull(aircraft, "自定义层级应当被读回来");
        assertEquals("飞行器", aircraft.displayName);
        assertEquals(130, aircraft.order);
        assertEquals("aircraft", aircraft.id, "id 由 JSON 的键补上");
    }

    @Test
    @DisplayName("文件不存在时生成默认配置并落盘")
    void generatesDefaultsWhenMissing() {
        Path file = LayerConfigIO.configPath(tempDir);
        assertTrue(Files.notExists(file), "测试开始时不该有文件");

        LayerConfig config = LayerConfigIO.load(tempDir);

        assertNotNull(config);
        assertTrue(Files.isRegularFile(file), "应当顺手把默认配置写出来");
        assertEquals("gameplay", config.categories.get("key.categories.movement"));
    }

    @Test
    @DisplayName("玩家手写的注释也能读（宽容模式会跳过 // 与块注释）")
    void toleratesHandWrittenComments() throws IOException {
        // 注意：这里刻意不写尾逗号——Gson 的宽容模式支持注释，但不支持尾逗号。
        Files.writeString(LayerConfigIO.configPath(tempDir), """
                {
                  // 玩家自己加的注释
                  "enabled": true,
                  /* 块注释也应当被跳过 */
                  "packAuthorMode": true,
                  "categories": {
                    "key.categories.movement": "gameplay"
                  }
                }
                """);

        LayerConfig config = LayerConfigIO.load(tempDir);

        assertTrue(config.enabled);
        assertTrue(config.packAuthorMode);
        assertEquals("gameplay", config.categories.get("key.categories.movement"));
    }

    @Test
    @DisplayName("内容损坏时退回默认值而不是抛异常")
    void fallsBackOnBrokenFile() throws IOException {
        Files.writeString(LayerConfigIO.configPath(tempDir), "{ this is not json at all ");

        LayerConfig config = LayerConfigIO.load(tempDir);

        assertNotNull(config, "坏文件不该让游戏起不来");
        assertEquals("gameplay", config.categories.get("key.categories.movement"));
    }
}
