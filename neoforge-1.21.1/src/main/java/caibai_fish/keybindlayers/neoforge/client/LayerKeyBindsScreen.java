package caibai_fish.keybindlayers.neoforge.client;

import caibai_fish.keybindlayers.client.KeyMappingLayers;
import caibai_fish.keybindlayers.client.LayerNames;
import caibai_fish.keybindlayers.core.KeyLayer;
import caibai_fish.keybindlayers.core.KeyLayers;
import caibai_fish.keybindlayers.core.LayerRules;
import caibai_fish.keybindlayers.core.LockedKeybinds;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.settings.KeyModifier;

/**
 * 层级感知的按键设置界面（Minecraft 1.21.1 / NeoForge）。
 *
 * <p>替代原版 {@code KeyBindsScreen}：顶部按操作层级生成标签栏，点哪个标签就只列出
 * 那个层级的按键；「全部」标签下按层级分组并插入标题行，层级内再按 mod 分类分段
 * （见 {@link LayerKeyBindsList}）。冲突判定沿用层级规则。</p>
 *
 * <h2>为什么不继承原版 {@code KeyBindsScreen}</h2>
 * <p>它的 {@code keyBindsList} 与 {@code resetButton} 都是 private，且
 * {@code render()} 无条件读这两个字段——继承后无论怎么覆写都会踩到 null。
 * 因此直接继承 {@code OptionsSubScreen}，只复用它的标题与页脚布局能力。</p>
 *
 * <h2>与原版的一处已知差异</h2>
 * <p>原版 {@code KeyboardHandler} 里有一段“刚绑完键 20ms 内不响应 F11/F2”的保护，
 * 判据是 {@code screen instanceof KeyBindsScreen}。本界面不是它的子类，因此这层保护
 * 不生效：在等待改键时按 F11 会连带触发全屏。其余按键捕获逻辑（含 modifier 组合、
 * ESC 解绑、鼠标键绑定）与原版逐行对齐。</p>
 */
public class LayerKeyBindsScreen extends OptionsSubScreen {

    /** 与原版 {@code KeyBindsScreen.TITLE} 同一个翻译键，标题文字保持一致。 */
    private static final Component TITLE = Component.translatable("controls.keybinds.title");

    /** 「全部」标签的内部 id（层级 id 只含小写字母数字下划线，不会与之冲突）。 */
    private static final String TAB_ALL = "\u0000all";

    private static final int TAB_HEIGHT = 20;
    private static final int TAB_GAP = 4;
    private static final int TAB_MARGIN = 10;
    /** 鼠标滚轮一格横向滚动的像素数。 */
    private static final int TAB_SCROLL_STEP = 40;
    /** 改键被拒绝时那条提示显示多久（毫秒）。 */
    private static final long NOTICE_MILLIS = 3000L;

    /** 正在等待改键的绑定；与原版一样是 public。 */
    public KeyMapping selectedKey;
    /** 最近一次完成改键的时间戳；原版由 {@code KeyboardHandler} 读取。 */
    public long lastKeySelection;

    /** 改键被拒绝（例如想绑到锁定按键占用的键位）时的临时提示。 */
    private Component notice;
    private long noticeUntil;

    private LayerKeyBindsList list;
    private Button resetButton;
    private final List<Tab> tabs = new ArrayList<>();
    private String selectedTabId = TAB_ALL;
    /** 标签栏的 y 坐标；标签栏固定占一行，标签多了横向滚动。 */
    private int tabY;
    /** 标签栏的横向滚动偏移（像素）。 */
    private int tabScrollOffset;

    private InputConstants.Key lastPressedKey = InputConstants.UNKNOWN;
    private InputConstants.Key lastPressedModifier = InputConstants.UNKNOWN;
    private boolean isLastKeyHeldDown;
    private boolean isLastModifierHeldDown;

    public LayerKeyBindsScreen(Screen lastScreen, Options options) {
        super(lastScreen, options, TITLE);
    }

    // ------------------------------------------------------------------ 生命周期

    @Override
    protected void init() {
        // 建列表之前先补采一次作用域：让「只要装备/手持某物」的按键并进游戏内层，
        // 免得玩家必须先进入一次世界才看得到正确分层。
        InGameContextSampler.sampleNow();
        super.init();
    }

    @Override
    protected void addContents() {
        this.list = new LayerKeyBindsList(this, this.minecraft);
        this.addRenderableWidget(this.list);

        this.tabs.clear();
        this.tabs.addAll(this.buildTabs());
        if (this.currentTab() == null) {
            // 之前选中的层级已经不在了（例如刚在配置界面上删掉），退回「全部」。
            this.selectedTabId = TAB_ALL;
        }
        for (Tab tab : this.tabs) {
            this.addRenderableWidget(tab.button);
        }
        // 列表构造时标签栏还是空的，这里补一次重建，让「全部」标签带上层级标题行。
        this.list.rebuild();
        this.updateTabStates();
    }

    @Override
    protected void addOptions() {
        // 本界面不用 OptionsList，全部内容都在 addContents 里手工布置。
    }

    @Override
    protected void addFooter() {
        this.resetButton = Button.builder(Component.translatable("controls.resetAll"), button -> {
            for (KeyMapping mapping : this.options.keyMappings) {
                mapping.setToDefault();
            }
            this.list.resetMappingAndUpdateButtons();
        }).build();

        LinearLayout footer = (LinearLayout) this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(this.resetButton);
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).build());
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();

        // 标签栏固定占一行：标签多了就整行横向滚动（鼠标滚轮），而不是换行把列表挤扁。
        this.tabY = this.layout.getHeaderHeight() + 2;
        this.clampTabScroll();
        this.layoutTabs();

        int listTop = this.tabY + TAB_HEIGHT + 4;
        int listHeight = Math.max(40, this.height - listTop - this.layout.getFooterHeight() - 4);
        this.list.updateSizeAndPosition(this.width, listHeight, listTop);
    }

    /** 把所有标签排在同一个 y 上，只让落在可视范围内的参与渲染与点击。 */
    private void layoutTabs() {
        int x = TAB_MARGIN - this.tabScrollOffset;
        for (Tab tab : this.tabs) {
            tab.button.setPosition(x, this.tabY);
            // 部分露出的标签照样渲染：被截断反而能看出「后面还有」，滚动位置一目了然。
            tab.button.visible = x + tab.width > TAB_MARGIN && x < this.width - TAB_MARGIN;
            x += tab.width + TAB_GAP;
        }
    }

    /** 标签栏内容总宽（含间距）。 */
    private int tabsWidth() {
        int total = 0;
        for (Tab tab : this.tabs) {
            total += tab.width + TAB_GAP;
        }
        return total;
    }

    /** 最大可滚动偏移；一行放得下时为 0。 */
    private int maxTabScroll() {
        return Math.max(0, this.tabsWidth() - (this.width - TAB_MARGIN * 2));
    }

    private void clampTabScroll() {
        this.tabScrollOffset = Math.max(0, Math.min(this.tabScrollOffset, this.maxTabScroll()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // 鼠标在标签栏上时，滚轮横向滚标签而不是滚列表。
        if (this.maxTabScroll() > 0
                && mouseY >= this.tabY && mouseY < this.tabY + TAB_HEIGHT) {
            this.tabScrollOffset -= (int) Math.round(scrollY * TAB_SCROLL_STEP);
            this.clampTabScroll();
            this.layoutTabs();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ------------------------------------------------------------------ 标签栏

    private List<Tab> buildTabs() {
        List<Tab> result = new ArrayList<>();
        result.add(this.newTab(TAB_ALL, Component.translatable("keybindlayers.tab.all"),
                layer -> true, true));

        // 按「标签名」把有按键的层级归组：同名的多个作用域共用一个标签
        // （例如 Sophisticated Backpacks 注册了 3 个 context，标签名都是它自己），
        // 但它们仍然是各自独立的层级——判定上互不冲突，只是显示在同一个标签下。
        Map<String, List<KeyLayer>> groups = new LinkedHashMap<>();
        for (KeyLayer layer : KeyLayers.all()) {
            if (!hasMappingsFor(layer)) {
                continue;
            }
            groups.computeIfAbsent(this.tabName(layer).getString(), key -> new ArrayList<>()).add(layer);
        }
        for (List<KeyLayer> group : groups.values()) {
            Set<String> ids = new HashSet<>();
            for (KeyLayer layer : group) {
                ids.add(layer.id());
            }
            result.add(this.newTab(group.get(0).id(), this.tabName(group.get(0)),
                    layer -> ids.contains(layer.id()), false));
        }

        return result;
    }

    private Tab newTab(String id, Component name, Predicate<KeyLayer> filter, boolean showHeaders) {
        int width = Math.max(40, this.font.width(name) + 14);
        Button button = Button.builder(name, b -> this.selectTab(id)).bounds(0, 0, width, TAB_HEIGHT).build();
        return new Tab(id, filter, showHeaders, button, width);
    }

    private void selectTab(String id) {
        if (this.selectedTabId.equals(id)) {
            return;
        }
        this.selectedTabId = id;
        this.list.rebuild();
        this.updateTabStates();
    }

    private void updateTabStates() {
        for (Tab tab : this.tabs) {
            // 已选中的标签按钮置灰，充当“当前页”的视觉指示。
            tab.button.active = !tab.id.equals(this.selectedTabId);
        }
    }

    private Tab currentTab() {
        for (Tab tab : this.tabs) {
            if (tab.id.equals(this.selectedTabId)) {
                return tab;
            }
        }
        return null;
    }

    /** 某个层级是否归属于当前选中的标签。 */
    boolean matchesCurrentTab(KeyLayer layer) {
        Tab tab = this.currentTab();
        return tab == null || tab.filter.test(layer);
    }

    /**
     * 当前是否显示层级标题行。
     *
     * <p>两种情况要显示：跨层级的「全部」标签；以及一个标签下合并了多个层级时
     * ——同名层级会并进同一个标签，按键混在一起就分不清哪些属于哪一层了。</p>
     */
    boolean showsLayerHeaders() {
        Tab tab = this.currentTab();
        if (tab == null) {
            return false;
        }
        return tab.showHeaders || this.layersInCurrentTab() > 1;
    }

    /** 当前标签匹配了几个层级。 */
    private int layersInCurrentTab() {
        Tab tab = this.currentTab();
        if (tab == null) {
            return 0;
        }
        int count = 0;
        for (KeyLayer layer : KeyLayers.all()) {
            if (tab.filter.test(layer)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 标签上显示的名字。
     *
     * <p>临时层（{@code category:xxx}）与自定义作用域层（{@code context:xxx}）都优先用
     * <strong>所属 mod 的显示名</strong>。这样同一个 mod 的多个层级会得到同一个标签名，
     * 从而并进同一个标签（见 {@link #buildTabs}）——但它们仍然是各自独立的层级，
     * 判定上互不冲突，只是显示在一起。</p>
     *
     * <p>不要拿「该层某个按键的分类名」充数：JEI 的 19 个按键分属多个细分类，
     * 那样会变成「JEI（鼠标悬停）」这种以偏概全的叫法。取不到 mod 名时才退回
     * {@link LayerNames}（分类的本地化名）。</p>
     */
    private Component tabName(KeyLayer layer) {
        String id = layer.id();
        if (KeyLayers.displayName(id) != null) {
            // 平台侧已经给 context 层设好 mod 显示名。
            return LayerNames.of(layer);
        }
        if (id.startsWith(LayerRules.OWN_LAYER_PREFIX) || id.startsWith(KeyMappingLayers.CONTEXT_LAYER_PREFIX)) {
            String modName = modDisplayNameOf(id);
            if (modName != null) {
                return Component.literal(modName);
            }
        }
        return LayerNames.of(layer);
    }

    /**
     * 从层级 id 里认出它属于哪个 mod，返回 mod 的显示名；认不出返回 {@code null}。
     *
     * <p>按 {@code .} 与 {@code $} 切段逐个拿去问 {@link ModList}：</p>
     * <ul>
     *   <li>{@code category:key.category.sophisticatedbackpacks.main} → {@code sophisticatedbackpacks}；</li>
     *   <li>{@code context:net.p3pp3rf1y.sophisticatedbackpacks.client.KeybindHandler$Backpack…}
     *       → 同样命中 {@code sophisticatedbackpacks}。</li>
     * </ul>
     *
     * <p>原版分类（{@code key.categories.misc} 之类）切出来的段都不是已加载的 modid，
     * 因此会返回 {@code null}，继续用分类自己的本地化名。</p>
     */
    private static String modDisplayNameOf(String layerId) {
        for (String part : layerId.split("[.$]")) {
            if (part.length() < 2) {
                continue;
            }
            Optional<? extends ModContainer> container = ModList.get().getModContainerById(part);
            if (container.isPresent()) {
                return container.get().getModInfo().getDisplayName();
            }
        }
        return null;
    }

    private boolean hasMappingsFor(KeyLayer layer) {
        for (KeyMapping mapping : this.options.keyMappings) {
            if (LockedKeybinds.isLocked(mapping.getName())) {
                continue;   // 锁定的按键不出现在界面里，不该为它生成标签
            }
            KeyLayer other = caibai_fish.keybindlayers.client.KeyMappingLayers.layerOf(mapping);
            if (other.id().equals(layer.id())) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ 供列表使用

    KeyMapping selectedKey() {
        return this.selectedKey;
    }

    /**
     * 是否正在等待玩家按键（点过「改键」按钮但还没按完）。
     *
     * <p>供 {@code KeyboardHandlerMixin} 判断保护窗口：等待期间玩家按的<strong>任何</strong>键
     * 都可能是想绑的键，不能顺手触发 F11（切全屏）或 F2（截图）。原版只按「距上次选中 20ms」
     * 判断，那个窗口窄得几乎没用——等待期间按 F11 照样切全屏。</p>
     */
    public boolean isAwaitingRebind() {
        return this.selectedKey != null;
    }

    /** 点击某一行的「改键」按钮：进入等待按键状态。 */
    void selectKey(KeyMapping mapping) {
        this.selectedKey = mapping;
    }

    /**
     * 该键位被哪个锁定按键占用了；没被占用返回 {@code null}。
     *
     * <p>锁定按键（快捷栏 1-9、全屏 F11）不在列表里显示，但仍在
     * {@code options.keyMappings} 中参与冲突检测。所以别的键想绑到它们占用的键位时，
     * 必须在改键阶段就拦下来——否则玩家会看到一条「冲突」红字却找不到冲突对象
     * （它被隐藏了）。返回占用的那个按键是为了在提示里点名，玩家才知道是谁占的。</p>
     */
    private KeyMapping findLockedKeyUsing(InputConstants.Key candidate) {
        if (candidate == null || candidate.equals(InputConstants.UNKNOWN)) {
            return null;
        }
        for (KeyMapping mapping : this.options.keyMappings) {
            if (LockedKeybinds.isLocked(mapping.getName()) && candidate.equals(mapping.getKey())) {
                return mapping;
            }
        }
        return null;
    }

    /** 拒绝这次改键：提示「已被哪个功能占用」并取消等待状态（原绑定保持不变）。 */
    private void rejectKeybind(KeyMapping owner) {
        this.selectedKey = null;
        this.showNotice(Component.translatable("keybindlayers.keybind.reserved", owner.getDisplayName()));
        this.list.resetMappingAndUpdateButtons();
    }

    private void showNotice(Component text) {
        this.notice = text;
        this.noticeUntil = net.minecraft.Util.getMillis() + NOTICE_MILLIS;
    }

    // ------------------------------------------------------------------ 输入处理（对齐原版）

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.selectedKey != null) {
            InputConstants.Key candidate = InputConstants.Type.MOUSE.getOrCreate(button);
            KeyMapping owner = this.findLockedKeyUsing(candidate);
            if (owner != null) {
                this.rejectKeybind(owner);
            } else {
                this.options.setKey(this.selectedKey, candidate);
                this.selectedKey = null;
                this.list.resetMappingAndUpdateButtons();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.selectedKey != null) {
            InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
            if (this.lastPressedModifier == InputConstants.UNKNOWN && KeyModifier.isKeyCodeModifier(key)) {
                this.lastPressedModifier = key;
                this.isLastModifierHeldDown = true;
            } else {
                this.lastPressedKey = key;
                this.isLastKeyHeldDown = true;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (this.selectedKey != null && (!Minecraft.ON_OSX || scanCode != 63)) {
            if (keyCode == 256) {
                // ESC：解除绑定。
                this.selectedKey.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.UNKNOWN);
                this.options.setKey(this.selectedKey, InputConstants.UNKNOWN);
                this.lastPressedKey = InputConstants.UNKNOWN;
                this.lastPressedModifier = InputConstants.UNKNOWN;
                this.isLastKeyHeldDown = false;
                this.isLastModifierHeldDown = false;
            } else {
                InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
                if (this.lastPressedKey.equals(key)) {
                    this.isLastKeyHeldDown = false;
                } else if (this.lastPressedModifier.equals(key)) {
                    this.isLastModifierHeldDown = false;
                }

                if (this.isLastKeyHeldDown || this.isLastModifierHeldDown) {
                    // 还有键没抬起，保持等待状态。
                    return true;
                }
                // 候选键：优先用主键；只按住修饰键时就是修饰键本身。
                InputConstants.Key candidate = !this.lastPressedKey.equals(InputConstants.UNKNOWN)
                        ? this.lastPressedKey
                        : this.lastPressedModifier;
                KeyMapping owner = this.findLockedKeyUsing(candidate);
                if (owner != null) {
                    // 被锁定按键占用的键位不给别人用，直接拒绝并点名提示。
                    this.rejectKeybind(owner);
                } else if (!this.lastPressedKey.equals(InputConstants.UNKNOWN)) {
                    this.selectedKey.setKeyModifierAndCode(
                            KeyModifier.getKeyModifier(this.lastPressedModifier), this.lastPressedKey);
                    this.options.setKey(this.selectedKey, this.lastPressedKey);
                } else {
                    this.selectedKey.setKeyModifierAndCode(KeyModifier.NONE, this.lastPressedModifier);
                    this.options.setKey(this.selectedKey, this.lastPressedModifier);
                }
                this.lastPressedKey = InputConstants.UNKNOWN;
                this.lastPressedModifier = InputConstants.UNKNOWN;
            }
            this.selectedKey = null;
            this.lastKeySelection = net.minecraft.Util.getMillis();
            this.list.resetMappingAndUpdateButtons();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        boolean anyChanged = false;
        for (KeyMapping mapping : this.options.keyMappings) {
            if (!mapping.isDefault()) {
                anyChanged = true;
                break;
            }
        }
        this.resetButton.active = anyChanged;

        // 标签栏一行放不下时，用左右箭头提示可以滚轮横向滚动。
        if (this.maxTabScroll() > 0) {
            int arrowY = this.tabY + (TAB_HEIGHT - 8) / 2;
            if (this.tabScrollOffset > 0) {
                guiGraphics.drawString(this.font, "<", TAB_MARGIN - 8, arrowY, 0xFFFFFFFF);
            }
            if (this.tabScrollOffset < this.maxTabScroll()) {
                guiGraphics.drawString(this.font, ">", this.width - TAB_MARGIN + 2, arrowY, 0xFFFFFFFF);
            }
        }

        // 改键被拒绝的提示（例如想绑到锁定按键占用的键位），显示几秒后自动消失。
        if (this.notice != null && net.minecraft.Util.getMillis() < this.noticeUntil) {
            guiGraphics.drawCenteredString(this.font, this.notice,
                    this.width / 2, this.height - this.layout.getFooterHeight() - 12, 0xFFFF5555);
        }
    }

    // ------------------------------------------------------------------ 标签数据

    private static final class Tab {
        final String id;
        final Predicate<KeyLayer> filter;
        final boolean showHeaders;
        final Button button;
        final int width;

        Tab(String id, Predicate<KeyLayer> filter, boolean showHeaders, Button button, int width) {
            this.id = id;
            this.filter = filter;
            this.showHeaders = showHeaders;
            this.button = button;
            this.width = width;
        }
    }
}
