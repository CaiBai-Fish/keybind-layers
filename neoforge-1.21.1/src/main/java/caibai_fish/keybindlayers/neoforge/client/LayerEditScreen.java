package caibai_fish.keybindlayers.neoforge.client;

import caibai_fish.keybindlayers.client.KeybindLayersClient;
import caibai_fish.keybindlayers.core.KeyLayers;
import caibai_fish.keybindlayers.core.LayerConfig;
import caibai_fish.keybindlayers.core.LayerDefinition;
import caibai_fish.keybindlayers.core.LayerManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * 新建 / 编辑一个按键层级的子界面。
 *
 * <p>三个字段：层级 id（新建后可改前锁定）、显示名（可留空，回退到语言文件）、深度
 * （越小越底层）。保存时走 {@link LayerManager} 校验并写回配置，然后立即生效。</p>
 */
public class LayerEditScreen extends Screen {

    private final LayerManagerScreen parent;
    /** 正在编辑的层级 id；{@code null} 表示新建。 */
    private final String layerId;

    private EditBox idBox;
    private EditBox nameBox;
    private EditBox orderBox;
    private Component error;

    public LayerEditScreen(LayerManagerScreen parent, String layerId) {
        super(Component.translatable(layerId == null
                ? "keybindlayers.config.create"
                : "keybindlayers.config.edit"));
        this.parent = parent;
        this.layerId = layerId;
    }

    @Override
    protected void init() {
        LayerConfig config = KeybindLayersClient.config();
        boolean builtin = this.layerId != null && KeyLayers.isBuiltin(this.layerId);
        LayerDefinition definition = (this.layerId == null || config == null)
                ? null
                : config.layers.get(this.layerId);

        int order = definition != null
                ? definition.order
                : (builtin ? KeyLayers.require(this.layerId).order() : LayerDefinition.DEFAULT_ORDER);
        String displayName = definition != null && definition.displayName != null ? definition.displayName : "";

        int fieldWidth = 200;
        int left = this.width / 2 - fieldWidth / 2;

        this.idBox = new EditBox(this.font, left, 56, fieldWidth, 20,
                Component.translatable("keybindlayers.config.field.id"));
        this.idBox.setMaxLength(32);
        this.idBox.setFilter(text -> text.matches("[a-z0-9_]*"));
        if (this.layerId != null) {
            this.idBox.setValue(this.layerId);
            // id 是层级的主键，编辑期不允许改（改名等于换一个层）。
            this.idBox.active = false;
        }
        this.addRenderableWidget(this.idBox);

        this.nameBox = new EditBox(this.font, left, 100, fieldWidth, 20,
                Component.translatable("keybindlayers.config.field.name"));
        this.nameBox.setMaxLength(48);
        this.nameBox.setValue(displayName);
        this.addRenderableWidget(this.nameBox);

        this.orderBox = new EditBox(this.font, left, 144, fieldWidth, 20,
                Component.translatable("keybindlayers.config.field.order"));
        this.orderBox.setMaxLength(4);
        this.orderBox.setFilter(text -> text.matches("\\d*"));
        this.orderBox.setValue(Integer.toString(order));
        // 内置层级的深度由代码固定，配置里写了也会被忽略。
        this.orderBox.active = !builtin;
        this.addRenderableWidget(this.orderBox);

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.save())
                .bounds(this.width / 2 - 154, this.height - 32, 150, 20)
                .build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose())
                .bounds(this.width / 2 + 4, this.height - 32, 150, 20)
                .build());

        this.setInitialFocus(this.idBox);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);

        int left = this.width / 2 - 100;
        guiGraphics.drawString(this.font, Component.translatable("keybindlayers.config.field.id"),
                left, 44, 0xFFA0A0A0);
        guiGraphics.drawString(this.font, Component.translatable("keybindlayers.config.field.name"),
                left, 88, 0xFFA0A0A0);
        guiGraphics.drawString(this.font, Component.translatable("keybindlayers.config.field.order"),
                left, 132, 0xFFA0A0A0);

        if (this.error != null) {
            guiGraphics.drawCenteredString(this.font, this.error, this.width / 2, this.height - 58, 0xFFFF5555);
        }
    }

    private void save() {
        LayerConfig config = KeybindLayersClient.config();
        if (config == null) {
            this.onClose();
            return;
        }
        String id = LayerManager.normalizeId(this.idBox.getValue());
        String displayName = this.nameBox.getValue();

        int order;
        try {
            order = this.orderBox.getValue().isEmpty()
                    ? LayerDefinition.DEFAULT_ORDER
                    : Integer.parseInt(this.orderBox.getValue());
        } catch (NumberFormatException e) {
            order = LayerDefinition.DEFAULT_ORDER;
        }

        try {
            if (this.layerId == null) {
                LayerManager.create(config, id, displayName, order);
            } else {
                LayerManager.update(config, this.layerId, displayName, order);
            }
            KeybindLayersClient.saveAndApply();
            this.minecraft.setScreen(this.parent);
        } catch (IllegalArgumentException e) {
            this.error = this.describe(e);
        }
    }

    /** 把核心层抛出的异常翻译成用户能看懂的提示。 */
    private Component describe(IllegalArgumentException exception) {
        String message = String.valueOf(exception.getMessage());
        if (message.contains("Invalid layer id")) {
            return Component.translatable("keybindlayers.config.error.id_format");
        }
        if (message.contains("already exists")) {
            return Component.translatable("keybindlayers.config.error.id_taken");
        }
        if (message.contains("reserved")) {
            return Component.translatable("keybindlayers.config.error.id_builtin");
        }
        if (message.contains("Unknown layer")) {
            return Component.translatable("keybindlayers.config.error.unknown");
        }
        return Component.literal(message);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}
