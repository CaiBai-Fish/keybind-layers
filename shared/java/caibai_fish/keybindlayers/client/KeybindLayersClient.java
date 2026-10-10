package caibai_fish.keybindlayers.client;

import caibai_fish.keybindlayers.core.KeyLayer;
import caibai_fish.keybindlayers.core.KeyLayers;
import caibai_fish.keybindlayers.core.LayerConfig;
import caibai_fish.keybindlayers.core.LayerConfigIO;
import caibai_fish.keybindlayers.core.LayerRules;
import caibai_fish.keybindlayers.core.UnknownCategoryPolicy;
import java.nio.file.Path;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 客户端初始化与配置管理：读取 {@code config/keybindlayers.json}、写入判定规则表、
 * 并把层级管理界面的改动写回磁盘。
 *
 * <p>两个加载器各自的入口类只负责把 config 目录传进来，其余逻辑都在这里，
 * 保证跨版本行为完全一致。</p>
 */
public final class KeybindLayersClient {

    private static final Logger LOGGER = LoggerFactory.getLogger("keybindlayers");

    private static volatile boolean initialized = false;
    /** 当前生效的配置对象；层级管理界面直接编辑它，然后调用 {@link #saveAndApply()}。 */
    private static volatile LayerConfig config;
    /** 配置目录，由加载器入口传入。 */
    private static volatile Path configDir;

    private KeybindLayersClient() {
    }

    /** 幂等；由各加载器的 mod 入口在客户端环境下调用。 */
    public static void init(Path directory) {
        if (initialized) {
            return;
        }
        initialized = true;
        configDir = directory;
        LayerConfig loaded = LayerConfigIO.load(directory);
        config = loaded;
        apply(loaded);
        LOGGER.info("[Keybind Layers] Loaded {} custom layers, {} category assignments and {} per-key overrides (unknown category policy: {})",
                loaded.layers.size(), loaded.categories.size(), loaded.mappings.size(), loaded.unknownCategoryPolicy);
    }

    /**
     * 当前生效的配置对象（可变）。
     *
     * <p>层级管理界面就地修改它，再调用 {@link #saveAndApply()} 让改动落盘并生效。</p>
     */
    public static LayerConfig config() {
        return config;
    }

    /** 配置目录；尚未初始化时返回 {@code null}。 */
    public static Path configDir() {
        return configDir;
    }

    /**
     * 把配置里的改动写回磁盘并立即生效。
     *
     * <p>创建 / 修改 / 删除层级后调用；内部会重新应用全部规则，
     * 因此「控制」界面下一次重绘就能看到新的层级划分。</p>
     */
    public static void saveAndApply() {
        LayerConfig current = config;
        if (current == null) {
            return;
        }
        current.normalize();
        apply(current);
        Path directory = configDir;
        if (directory != null) {
            LayerConfigIO.save(directory, current);
        }
    }

    /** 把一份配置应用到判定规则表；供初始化与后续重新加载使用。 */
    public static void apply(LayerConfig config) {
        config.normalize();
        LayerRules.setEnabled(config.enabled);
        LayerRules.setSeparateModifierCombinations(config.separateModifierCombinations);
        LayerRules.setUseDeclaredKeyContext(config.useDeclaredKeyContext);
        LayerRules.setUnknownCategoryPolicy(UnknownCategoryPolicy.fromName(config.unknownCategoryPolicy));
        // 只清理由配置写入的赋值：其它 mod 通过 API 声明的归属必须保留下来。
        LayerRules.clearConfigAssignments();
        // 先应用层级定义，再应用分类 / 单键归属，保证被引用到的层级已经存在。
        KeyLayers.applyDefinitions(config.layers);

        for (Map.Entry<String, String> entry : config.categories.entrySet()) {
            LayerRules.assignCategoryFromConfig(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, String> entry : config.mappings.entrySet()) {
            String[] parts = LayerRules.parseMappingKey(entry.getKey());
            if (parts == null) {
                LOGGER.warn("[Keybind Layers] Ignoring malformed mapping override '{}' (expected 'category|mapping_name')",
                        entry.getKey());
                continue;
            }
            LayerRules.assignMappingFromConfig(parts[0], parts[1], entry.getValue());
        }
    }

    /** 某个绑定所属层级，供调试命令与日志使用。 */
    public static KeyLayer layerOfCategory(String category) {
        return LayerRules.layerOfCategory(category);
    }
}
