package caibai_fish.keybindlayers.client;

import caibai_fish.keybindlayers.client.KeybindLayersClient;
import caibai_fish.keybindlayers.client.LayerNames;
import caibai_fish.keybindlayers.core.KeyLayer;
import caibai_fish.keybindlayers.core.KeyLayers;
import caibai_fish.keybindlayers.core.LayerConfig;
import caibai_fish.keybindlayers.core.LayerManager;
import caibai_fish.keybindlayers.core.LayerRules;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * 按键层级管理界面（1.21.1 / NeoForge），由「模组列表 → Config」进入。
 *
 * <p>列出当前所有操作层级（内置 8 个 + 自定义），并提供：</p>
 * <ul>
 *   <li><b>整合包作者模式</b>开关：关闭时创建 / 编辑 / 删除按钮全部禁用，避免普通玩家误改；</li>
 *   <li><b>创建 / 编辑 / 删除</b>层级，改动立即写回 {@code config/keybindlayers.json} 并生效；</li>
 *   <li>删除前用 {@code ConfirmScreen} 提示该层级下还有多少处引用，确认后这些引用
 *       回退到未知分类策略。</li>
 * </ul>
 *
 * <p>NeoForge 规定一个 mod 只能注册一个 {@code IConfigScreenFactory}，所以如果将来还要
 * 增加配置子页，应在本屏幕内部做导航，而不是再注册一个配置界面。</p>
 */
public class LayerManagerScreen extends Screen {

    private static final Component TITLE = Component.translatable("keybindlayers.config.title");
    private static final int LIST_TOP = 62;
    private static final int LIST_BOTTOM_RESERVED = 40;

    private final Screen parent;
    private LayerList list;
    private Button createButton;

    public LayerManagerScreen(Screen parent) {
        super(TITLE);
        this.parent = parent;
    }

    @Override
    protected void init() {
        LayerConfig config = KeybindLayersClient.config();
        if (config == null) {
            // 配置尚未初始化（理论上不会发生）：直接返回，避免 NPE。
            this.onClose();
            return;
        }

        int centerX = this.width / 2;

        CycleButton<Boolean> authorMode = CycleButton.onOffBuilder(config.packAuthorMode)
                .withTooltip(value -> Tooltip.create(
                        Component.translatable("keybindlayers.config.author_mode.tooltip")))
                .create(Component.translatable("keybindlayers.config.author_mode"), (button, value) -> {
                    LayerConfig current = KeybindLayersClient.config();
                    if (current != null) {
                        current.packAuthorMode = value;
                        KeybindLayersClient.saveAndApply();
                    }
                    this.refresh();
                });
        authorMode.setPosition(centerX - 110, 34);
        authorMode.setWidth(220);
        this.addRenderableWidget(authorMode);

        this.createButton = Button.builder(Component.translatable("keybindlayers.config.create"),
                        button -> this.minecraft.setScreen(new LayerEditScreen(this, null)))
                .bounds(centerX - 154, this.height - 32, 150, 20)
                .build();
        this.addRenderableWidget(this.createButton);
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds(centerX + 4, this.height - 32, 150, 20)
                .build());

        this.list = new LayerList(this.minecraft, this.width,
                Math.max(40, this.height - LIST_TOP - LIST_BOTTOM_RESERVED - 24), LIST_TOP, 24);
        this.addRenderableWidget(this.list);
        this.refresh();
    }

    /** 重新读取配置状态并刷新列表与按钮可用性。 */
    void refresh() {
        LayerConfig config = KeybindLayersClient.config();
        boolean author = config != null && config.packAuthorMode;
        if (this.createButton != null) {
            this.createButton.active = author;
        }
        if (this.list != null) {
            this.list.rebuild();
            this.list.updateSizeAndPosition(this.width,
                    Math.max(40, this.height - LIST_TOP - LIST_BOTTOM_RESERVED - 24), LIST_TOP);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
        if (!this.authorButtonActive()) {
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("keybindlayers.config.readonly_hint"),
                    this.width / 2, this.height - 46, 0xFFA0A0A0);
        }
    }

    private boolean authorButtonActive() {
        LayerConfig config = KeybindLayersClient.config();
        return config != null && config.packAuthorMode;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    // ------------------------------------------------------------------ 层级列表

    /** 可删除的层级：排除 {@code category:xxx} 形式的临时层。 */
    private static boolean isListed(KeyLayer layer) {
        String id = layer.id();
        return !id.startsWith(LayerRules.OWN_LAYER_PREFIX) || KeyLayers.configDefinedIds().contains(id);
    }

    private class LayerList extends ContainerObjectSelectionList<LayerList.Entry> {

        LayerList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
            this.rebuild();
        }

        void rebuild() {
            this.clearEntries();
            for (KeyLayer layer : KeyLayers.all()) {
                if (isListed(layer)) {
                    this.addEntry(new Entry(layer));
                }
            }
        }

        @Override
        public int getRowWidth() {
            return 320;
        }

        class Entry extends ContainerObjectSelectionList.Entry<Entry> {

            private final KeyLayer layer;
            private final Button editButton;
            private final Button deleteButton;

            Entry(KeyLayer layer) {
                this.layer = layer;
                boolean builtin = KeyLayers.isBuiltin(layer.id());

                this.editButton = Button.builder(Component.translatable("keybindlayers.config.edit"),
                                button -> LayerManagerScreen.this.minecraft.setScreen(
                                        new LayerEditScreen(LayerManagerScreen.this, layer.id())))
                        .bounds(0, 0, 40, 20)
                        .build();

                this.deleteButton = Button.builder(Component.translatable("keybindlayers.config.delete"),
                                button -> LayerManagerScreen.this.confirmDelete(layer))
                        .bounds(0, 0, 40, 20)
                        .tooltip(builtin
                                ? Tooltip.create(Component.translatable("keybindlayers.config.builtin_hint"))
                                : Tooltip.create(Component.translatable(
                                        "keybindlayers.config.delete.tooltip",
                                        LayerManager.countReferences(KeybindLayersClient.config(), layer.id()))))
                        .build();

                this.refreshState();
            }

            private void refreshState() {
                boolean author = authorButtonActive();
                this.editButton.active = author;
                this.deleteButton.active = author && !KeyLayers.isBuiltin(this.layer.id());
            }

            @Override
            public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height,
                               int mouseX, int mouseY, boolean hovered, float partialTick) {
                this.refreshState();

                int right = LayerList.this.getRowRight();
                int y = top + height / 2 - 10;

                this.deleteButton.setPosition(right - 44, y);
                this.deleteButton.render(guiGraphics, mouseX, mouseY, partialTick);
                this.editButton.setPosition(right - 88, y);
                this.editButton.render(guiGraphics, mouseX, mouseY, partialTick);

                int textY = top + height / 2 - 4;
                guiGraphics.drawString(LayerManagerScreen.this.font,
                        LayerNames.of(this.layer), left, textY, 0xFFFFFFFF);

                Component detail = Component.literal(this.layer.id() + "  ·  " + this.layer.order()
                        + (KeyLayers.isBuiltin(this.layer.id()) ? "  ·  " + Component.translatable(
                                "keybindlayers.config.builtin").getString() : ""));
                int detailX = left + LayerManagerScreen.this.font.width(LayerNames.of(this.layer)) + 8;
                if (detailX < right - 92) {
                    guiGraphics.drawString(LayerManagerScreen.this.font, detail, detailX, textY, 0xFF8C8C8C);
                }
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return List.of(this.editButton, this.deleteButton);
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return List.of(this.editButton, this.deleteButton);
            }
        }
    }

    /** 删除确认：提示引用数量，确认后引用自动回退。 */
    private void confirmDelete(KeyLayer layer) {
        LayerConfig config = KeybindLayersClient.config();
        if (config == null) {
            return;
        }
        int references = LayerManager.countReferences(config, layer.id());
        this.minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                try {
                    LayerManager.delete(KeybindLayersClient.config(), layer.id());
                    KeybindLayersClient.saveAndApply();
                } catch (RuntimeException e) {
                    // 内置层级等非法删除：忽略请求，界面照常返回。
                }
            }
            // 回到本界面；setScreen 会重新走 init()，列表自然刷新。
            this.minecraft.setScreen(this);
        },
                Component.translatable("keybindlayers.config.delete.title"),
                Component.translatable("keybindlayers.config.delete.message", LayerNames.of(layer), references)));
    }
}
