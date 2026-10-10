package caibai_fish.keybindlayers.client;

import caibai_fish.keybindlayers.client.KeyMappingLayers;
import caibai_fish.keybindlayers.client.LayerNames;
import caibai_fish.keybindlayers.core.KeyLayer;
import caibai_fish.keybindlayers.core.LockedKeybinds;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * 层级感知的按键列表（Minecraft 1.21.1 / NeoForge）。
 *
 * <p>原版的 {@code KeyBindsList$KeyEntry} 与 {@code CategoryEntry} 构造器都是包私有
 * （{@code Entry#refreshEntry()} 也是包私有抽象方法），工程外无法实例化或继承，
 * 因此这里自建一份等价实现。几何与交互对齐原版（已用 javap 逐行核对）：</p>
 * <ul>
 *   <li>条目高度 20，行宽 340（与原版 {@code getRowWidth()} 一致，按钮坐标依赖它）；</li>
 *   <li>「改键」按钮 75x20、「重置」按钮 50x20，位置由滚动条位置向左反推；</li>
 *   <li>冲突时按钮文案变成红色 {@code [ 键名 ]}，并在改键按钮左侧画 3px 不透明红条；</li>
 *   <li>正在等待改键的那一行，文案变成黄色 {@code > 键名 <}。</li>
 * </ul>
 *
 * <h2>两级分组</h2>
 * <p>列表按「操作层级 → 分类」组织：</p>
 * <ul>
 *   <li><b>层级标题</b>（居中、白色）：只在「全部」「未分层」这类跨层级的标签下显示；</li>
 *   <li><b>分类标题</b>（左对齐、灰色）：层级内按 {@code KeyMapping#getCategory()} 分组，
 *       也就是按 mod（原版按键则按原版分类）分段。层内只有一个分类时不显示，
 *       免得「未分层」标签下标题与层级标题重复。</li>
 * </ul>
 *
 * <p>冲突判定走 {@link KeyMappingLayers#conflictsOf}，即「键相同、修饰键相同 <em>且</em>
 * 属于同一层级」。</p>
 */
public class LayerKeyBindsList extends ContainerObjectSelectionList<LayerKeyBindsList.Entry> {

    /** 与原版 {@code KeyBindsList.ITEM_HEIGHT} 一致。 */
    private static final int ITEM_HEIGHT = 20;
    /** 与原版 {@code KeyBindsList.getRowWidth()} 一致。 */
    private static final int ROW_WIDTH = 340;
    /** 分类标题文字颜色：稍暗的灰，与居中的层级标题区分开。 */
    private static final int CATEGORY_TEXT_COLOR = 0xFF9E9E9E;

    private final LayerKeyBindsScreen screen;

    public LayerKeyBindsList(LayerKeyBindsScreen screen, Minecraft minecraft) {
        super(minecraft, screen.width, screen.height, 0, ITEM_HEIGHT);
        this.screen = screen;
        this.rebuild();
    }

    /**
     * 按当前选中的标签重建全部条目（切换标签、层级定义变化、界面初次打开时调用）。
     */
    public void rebuild() {
        this.clearEntries();
        // 锁定的按键（快捷栏 1-9、全屏等）直接从设置界面里消失。先过滤再分组，
        // 免得它们所在的分类/层级留下一个没有按键的空标题。
        KeyMapping[] visible = Arrays.stream(this.minecraft.options.keyMappings)
                .filter(mapping -> !LockedKeybinds.isLocked(mapping.getName()))
                .toArray(KeyMapping[]::new);
        Map<KeyLayer, List<KeyMapping>> byLayer = KeyMappingLayers.groupByLayer(visible);
        for (Map.Entry<KeyLayer, List<KeyMapping>> layerGroup : byLayer.entrySet()) {
            KeyLayer layer = layerGroup.getKey();
            if (!this.screen.matchesCurrentTab(layer)) {
                continue;
            }
            if (this.screen.showsLayerHeaders()) {
                this.addEntry(new LayerHeaderEntry(this, layer));
            }

            Map<String, List<KeyMapping>> byCategory = groupByCategory(layerGroup.getValue());
            boolean showCategoryHeaders = byCategory.size() > 1;
            for (Map.Entry<String, List<KeyMapping>> categoryGroup : byCategory.entrySet()) {
                if (showCategoryHeaders) {
                    this.addEntry(new CategoryHeaderEntry(this, categoryGroup.getKey()));
                }
                for (KeyMapping mapping : categoryGroup.getValue()) {
                    this.addEntry(new KeyEntry(this, mapping));
                }
            }
        }
        this.setScrollAmount(0.0D);
    }

    /** 按 {@code KeyMapping#getCategory()} 分组，保持传入顺序。 */
    private static Map<String, List<KeyMapping>> groupByCategory(List<KeyMapping> mappings) {
        Map<String, List<KeyMapping>> grouped = new LinkedHashMap<>();
        for (KeyMapping mapping : mappings) {
            String category = mapping.getCategory();
            grouped.computeIfAbsent(category == null ? "" : category, key -> new ArrayList<>()).add(mapping);
        }
        return grouped;
    }

    @Override
    public int getRowWidth() {
        return ROW_WIDTH;
    }

    /** 重新计算每行的冲突标记（对应原版 {@code refreshEntries}）。 */
    public void refreshEntries() {
        this.children().forEach(Entry::refreshEntry);
    }

    /** 重建按键索引并刷新全部行（对应原版 {@code resetMappingAndUpdateButtons}）。 */
    public void resetMappingAndUpdateButtons() {
        KeyMapping.resetMapping();
        this.refreshEntries();
    }

    // ------------------------------------------------------------ 供条目使用的访问器
    // 原版的条目是内部类，可以直接读列表的 protected 成员；这里是静态嵌套类，
    // 跨包访问 protected 成员不合法，所以开几个窄口子。

    Minecraft minecraft() {
        return this.minecraft;
    }

    Font font() {
        return this.minecraft.font;
    }

    int scrollbarPosition() {
        return this.getScrollbarPosition();
    }

    LayerKeyBindsScreen screen() {
        return this.screen;
    }

    // ------------------------------------------------------------------ 条目基类

    /** 列表行基类，对应原版 {@code KeyBindsList$Entry}。 */
    public abstract static class Entry extends ContainerObjectSelectionList.Entry<Entry> {

        /** 重新计算该行的显示内容（冲突标记、按钮文案等）。 */
        abstract void refreshEntry();
    }

    // ------------------------------------------------------------------ 层级标题行

    /** 层级标题行，绘制方式对齐原版 {@code CategoryEntry}：居中、无阴影、贴条目底部。 */
    public static class LayerHeaderEntry extends Entry {

        private final LayerKeyBindsList list;
        private final Component name;
        private final int textWidth;

        LayerHeaderEntry(LayerKeyBindsList list, KeyLayer layer) {
            this.list = list;
            this.name = LayerNames.of(layer);
            this.textWidth = list.font().width(this.name);
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            guiGraphics.drawString(this.list.font(), this.name,
                    this.list.width / 2 - this.textWidth / 2, top + height - 10, -1, false);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return Collections.emptyList();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return Collections.emptyList();
        }

        @Override
        void refreshEntry() {
        }
    }

    // ------------------------------------------------------------------ 分类标题行

    /**
     * 分类标题行：层级之内再按 mod / 原版分类分段。
     *
     * <p>分类字符串本身就是一个翻译键（原版分类、JEI 的细分类都是），也可能是 mod 直接
     * 写的人可读名字（例如 Xaero's 的 {@code Xaero's Minimap}）——{@code translatable}
     * 找不到条目时会原样显示，两种情况都能正确处理。</p>
     */
    public static class CategoryHeaderEntry extends Entry {

        private final LayerKeyBindsList list;
        private final Component name;

        CategoryHeaderEntry(LayerKeyBindsList list, String category) {
            this.list = list;
            this.name = Component.translatable(category == null || category.isEmpty()
                    ? "keybindlayers.category.unknown"
                    : category);
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            // 左对齐，与按键名同一起点，视觉上从属于上面的层级标题。
            guiGraphics.drawString(this.list.font(), this.name,
                    left, top + height / 2 - 4, CATEGORY_TEXT_COLOR);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return Collections.emptyList();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return Collections.emptyList();
        }

        @Override
        void refreshEntry() {
        }
    }

    // ------------------------------------------------------------------ 按键行

    /** 一个按键绑定行，行为对齐原版 {@code KeyEntry}。 */
    public static class KeyEntry extends Entry {

        private static final Component RESET_BUTTON_TITLE = Component.translatable("controls.reset");

        private final LayerKeyBindsList list;
        private final KeyMapping key;
        private final Component name;
        private final Button changeButton;
        private final Button resetButton;
        private boolean hasCollision;

        KeyEntry(LayerKeyBindsList list, KeyMapping key) {
            this.list = list;
            this.key = key;
            this.name = Component.translatable(key.getName());

            this.changeButton = Button.builder(this.name, button -> {
                        list.screen().selectKey(key);
                        list.resetMappingAndUpdateButtons();
                    })
                    .bounds(0, 0, 75, 20)
                    .createNarration(supplier -> key.isUnbound()
                            ? Component.translatable("narrator.controls.unbound", this.name)
                            : Component.translatable("narrator.controls.bound", this.name, supplier.get()))
                    .build();

            this.resetButton = Button.builder(RESET_BUTTON_TITLE, button -> {
                        KeyModifiers.bind(this.key, KeyModifiers.noModifier(), this.key.getDefaultKey());
                        list.minecraft().options.setKey(this.key, this.key.getDefaultKey());
                        list.resetMappingAndUpdateButtons();
                    })
                    .bounds(0, 0, 50, 20)
                    .createNarration(supplier -> Component.translatable("narrator.controls.reset", this.name))
                    .build();

            this.refreshEntry();
        }

        @Override
        void refreshEntry() {
            this.changeButton.setMessage(this.key.getTranslatedKeyMessage());
            this.resetButton.active = !this.key.isDefault();
            this.hasCollision = false;

            // 与原版判据一致（same / hasKeyModifierConflict），额外要求同一修饰键与同一层级。
            List<KeyMapping> conflicts =
                    KeyMappingLayers.conflictsOf(this.key, this.list.minecraft().options.keyMappings);
            MutableComponent names = Component.empty();
            for (KeyMapping other : conflicts) {
                if (this.hasCollision) {
                    names.append(", ");
                }
                this.hasCollision = true;
                names.append(Component.translatable(other.getName()));
            }

            if (this.hasCollision) {
                this.changeButton.setMessage(Component.literal("[ ")
                        .append(this.changeButton.getMessage().copy().withStyle(ChatFormatting.WHITE))
                        .append(" ]")
                        .withStyle(ChatFormatting.RED));
                this.changeButton.setTooltip(Tooltip.create(
                        Component.translatable("controls.keybinds.duplicateKeybinds", names)));
            } else {
                this.changeButton.setTooltip(null);
            }

            if (this.list.screen().selectedKey() == this.key) {
                this.changeButton.setMessage(Component.literal("> ")
                        .append(this.changeButton.getMessage().copy()
                                .withStyle(ChatFormatting.WHITE, ChatFormatting.UNDERLINE))
                        .append(" <")
                        .withStyle(ChatFormatting.YELLOW));
            }
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            int resetX = this.list.scrollbarPosition() - this.resetButton.getWidth() - 10;
            int y = top - 2;
            this.resetButton.setPosition(resetX, y);
            this.resetButton.render(guiGraphics, mouseX, mouseY, partialTick);

            int changeX = resetX - 5 - this.changeButton.getWidth();
            this.changeButton.setPosition(changeX, y);
            this.changeButton.render(guiGraphics, mouseX, mouseY, partialTick);

            Font font = this.list.font();
            int textY = top + height / 2 - 4;
            guiGraphics.drawString(font, this.name, left, textY, -1);

            if (this.hasCollision) {
                int barX = this.changeButton.getX() - 6;
                guiGraphics.fill(barX, top - 1, barX + 3, top + height, 0xFFFF0000);
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(this.changeButton, this.resetButton);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(this.changeButton, this.resetButton);
        }
    }
}
