package caibai_fish.keybindlayers.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code config/keybindlayers.json} 的读写。
 *
 * <p>刻意只依赖 Gson 与 slf4j（两个加载器的 Minecraft 运行时都自带），
 * 这样配置逻辑可以完整地放在共享源码里。</p>
 *
 * <h2>为什么写出来的文件带注释</h2>
 * <p>JSON 规范本身不允许注释，但 Gson 的 {@link JsonReader} 在<strong>宽容模式</strong>下
 * 会跳过 {@code //} 与块注释。于是可以「写的时候手写一份带注释的文本、读的时候宽容解析」，
 * 玩家打开配置就能看懂每个字段干什么，不必翻文档。</p>
 *
 * <p>序列化因此不再走 {@code Gson#toJson}，而是由 {@link #toJsonc} 手写——字段就这么几个，
 * 换来的是自解释的配置文件。注意：<strong>只有 {@link #save} 写出的文件才带注释</strong>，
 * 玩家自己手写的配置有没有注释都能读。</p>
 */
public final class LayerConfigIO {

    private static final Logger LOGGER = LoggerFactory.getLogger("keybindlayers");
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    /** 配置文件名，位于加载器提供的 config 目录下。 */
    public static final String FILE_NAME = "keybindlayers.json";

    private LayerConfigIO() {
    }

    public static Path configPath(Path configDir) {
        return configDir.resolve(FILE_NAME);
    }

    /**
     * 读取配置；文件不存在时生成一份带内置默认分层的配置，解析失败时回退到默认值。
     *
     * <p>任何情况下都返回一个可用对象——配置文件写坏不应该让游戏起不来。</p>
     */
    public static LayerConfig load(Path configDir) {
        Path file = configPath(configDir);
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonReader jsonReader = new JsonReader(reader);
                // 宽容模式会跳过 // 与块注释，这样我们自己写进去的注释不会让配置读不出来。
                jsonReader.setLenient(true);
                LayerConfig config = GSON.fromJson(jsonReader, LayerConfig.class);
                if (config != null) {
                    config.normalize();
                    return config;
                }
                LOGGER.warn("[Keybind Layers] {} is empty, regenerating defaults", file);
            } catch (IOException | RuntimeException e) {
                LOGGER.error("[Keybind Layers] Failed to read {}, falling back to defaults", file, e);
                return LayerConfig.defaults();
            }
        }
        LayerConfig config = LayerConfig.defaults();
        save(configDir, config);
        return config;
    }

    /** 写回配置（带注释），失败只记录日志。 */
    public static void save(Path configDir, LayerConfig config) {
        Path file = configPath(configDir);
        try {
            Files.createDirectories(configDir);
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                writer.write(toJsonc(config));
            }
        } catch (IOException e) {
            LOGGER.error("[Keybind Layers] Failed to write {}", file, e);
        }
    }

    /** 生成带注释的配置文本（俗称 JSONC）。读取端是宽容模式的 {@link JsonReader}。 */
    public static String toJsonc(LayerConfig config) {
        StringBuilder out = new StringBuilder(4096);

        out.append("// Keybind Layers 配置。\n");
        out.append("// 本文件由 mod 自动生成，可以随意修改；// 与 /* */ 注释都会被读取端忽略。\n");
        out.append("// 删除某个字段或某一行，它会退回默认值。\n");
        out.append("{\n");

        out.append("  // 总开关。改成 false 就完全退回原版的冲突检测：所有同键都会标红。\n");
        out.append("  \"enabled\": ").append(config.enabled).append(",\n\n");

        out.append("  // 遇到没归过层的分类时怎么办：\n");
        out.append("  //   own_layer    每个分类各自成层（默认）——跨 mod 的同键不会报冲突\n");
        out.append("  //   shared_layer 所有未知分类共用一个 mods 层——更保守，跨 mod 同键仍会报冲突\n");
        out.append("  //   strict       所有分类视为同层，等同原版行为\n");
        out.append("  \"unknownCategoryPolicy\": ").append(quote(config.unknownCategoryPolicy)).append(",\n\n");

        out.append("  // 「整合包作者」模式。开启后可以在层级管理界面里创建 / 修改 / 删除层级；\n");
        out.append("  // 关闭时该界面只读，免得普通玩家误改整合包作者分好的层级。\n");
        out.append("  \"packAuthorMode\": ").append(config.packAuthorMode).append(",\n\n");

        out.append("  // 是否把组合键与单键分开看待。\n");
        out.append("  // true（默认）：W 与 Shift+W 不再互相标红——按 W 并不会触发 Shift+W；\n");
        out.append("  // false：退回原版行为。只影响冲突显示，不影响按键实际触发。\n");
        out.append("  \"separateModifierCombinations\": ").append(config.separateModifierCombinations).append(",\n\n");

        out.append("  // 是否采用按键自己声明的作用域（NeoForge 的 IN_GAME / GUI）。\n");
        out.append("  // 配置里显式写过的按键仍以配置为准，没写过的才看这个声明；\n");
        out.append("  // 1.20.1 侧没有这个读取器，此项在那边无效果。\n");
        out.append("  \"useDeclaredKeyContext\": ").append(config.useDeclaredKeyContext).append(",\n\n");

        out.append("  // 自定义层级：层级 id -> { displayName, order }。\n");
        out.append("  // 内置的 8 个层级不写在这里也始终存在；写进来只会改显示名，深度仍以内置值为准。\n");
        out.append("  // order 越小越靠前，只影响标签栏与列表排序，不参与冲突判定。\n");
        out.append("  \"layers\": ");
        appendLayers(out, config.layers);
        out.append(",\n\n");

        out.append("  // 分类 -> 层级 id。删掉某一行，那个分类就退回上面的 unknownCategoryPolicy。\n");
        out.append("  \"categories\": ");
        appendStringMap(out, config.categories);
        out.append(",\n\n");

        out.append("  // 「分类|按键名」-> 层级 id。优先级高于分类级，用来把分类里的个别按键\n");
        out.append("  // 单独挪一层，例如 \"key.categories.misc|key.advancements\": \"interface\"。\n");
        out.append("  \"mappings\": ");
        appendStringMap(out, config.mappings);
        out.append(",\n\n");

        out.append("  // 排查整合包问题时打开：把每个按键的层级判定结果打进日志。\n");
        out.append("  \"logLayerDecisions\": ").append(config.logLayerDecisions).append("\n");

        out.append("}\n");
        return out.toString();
    }

    /** 手写层级表，保持配置里原本的顺序。 */
    private static void appendLayers(StringBuilder out, Map<String, LayerDefinition> layers) {
        if (layers == null || layers.isEmpty()) {
            out.append("{}");
            return;
        }
        out.append("{\n");
        int index = 0;
        for (Map.Entry<String, LayerDefinition> entry : layers.entrySet()) {
            LayerDefinition definition = entry.getValue();
            out.append("    ").append(quote(entry.getKey())).append(": {");
            if (definition != null) {
                if (definition.displayName != null) {
                    out.append("\"displayName\": ").append(quote(definition.displayName)).append(", ");
                }
                out.append("\"order\": ").append(definition.order);
            }
            out.append('}');
            if (++index < layers.size()) {
                out.append(',');
            }
            out.append('\n');
        }
        out.append("  }");
    }

    /** 手写字符串表。 */
    private static void appendStringMap(StringBuilder out, Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            out.append("{}");
            return;
        }
        out.append("{\n");
        int index = 0;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            out.append("    ").append(quote(entry.getKey())).append(": ").append(quote(entry.getValue()));
            if (++index < map.size()) {
                out.append(',');
            }
            out.append('\n');
        }
        out.append("  }");
    }

    /** 借 Gson 做字符串转义，省得自己处理引号与反斜杠。 */
    private static String quote(String raw) {
        return GSON.toJson(raw == null ? "" : raw);
    }
}
