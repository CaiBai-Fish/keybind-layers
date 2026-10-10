package caibai_fish.keybindlayers.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code config/keybindlayers.json} 的读写。
 *
 * <p>刻意只依赖 Gson 与 slf4j（两个加载器的 Minecraft 运行时都自带），
 * 这样配置逻辑可以完整地放在共享源码里。</p>
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
                LayerConfig config = GSON.fromJson(reader, LayerConfig.class);
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

    /** 写回配置，失败只记录日志。 */
    public static void save(Path configDir, LayerConfig config) {
        Path file = configPath(configDir);
        try {
            Files.createDirectories(configDir);
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            LOGGER.error("[Keybind Layers] Failed to write {}", file, e);
        }
    }
}
