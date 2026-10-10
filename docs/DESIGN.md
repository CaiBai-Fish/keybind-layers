# 设计说明：层级感知的按键冲突检测

## 1. 原版是怎么判定冲突的

### 1.1 判定位置

设置界面「控制」页面的每一行是 `KeyBindsList$KeyEntry`。行内容刷新时执行的
`refreshEntry()` 里包含唯一的冲突判定（已用 `javap` 反汇编核实，1.20.1 与 1.21.1 一致）：

```java
this.hasCollision = false;
MutableComponent mutablecomponent = Component.empty();
if (!this.key.isUnbound()) {
    for (KeyMapping keymapping : KeyBindsList.this.minecraft.options.keyMappings) {
        if ((keymapping != this.key && this.key.same(keymapping)) || keymapping.hasKeyModifierConflict(this.key)) {
            if (this.hasCollision) {
                mutablecomponent.append(", ");
            }
            this.hasCollision = true;
            mutablecomponent.append(keymapping.getDisplayName());
        }
    }
}
if (this.hasCollision) {
    this.changeButton.setMessage(Component.literal("[ ")
        .append(this.changeButton.getMessage().copy().withStyle(ChatFormatting.WHITE))
        .append(" ]").withStyle(ChatFormatting.RED));
    this.changeButton.setTooltip(Tooltip.create(
        Component.translatable("controls.keybinds.duplicateKeybinds", mutablecomponent)));
} else {
    this.changeButton.setTooltip(null);
}
```

关键点：**判定与 `KeyMapping#getCategory()` 完全无关**——不同分类、不同用途的按键
只要键码相同就被视为冲突。冲突的视觉表现有三处：

1. 按钮文本变成红色 `[ 键名 ]`；
2. 按钮左侧画一条 3px 红色竖条（`render` 里按 `hasCollision` 绘制）；
3. 悬停提示走翻译键 `controls.keybinds.duplicateKeybinds`（"This key is also used for:\n%s"）。

### 1.2 字节码事实（决定 Mixin 怎么写）

对 `KeyBindsList$KeyEntry#refreshEntry()V` 反汇编，两个判据都是 `invokevirtual`，
**owner 都是 `net/minecraft/client/KeyMapping`**：

```
102: invokevirtual net/minecraft/client/KeyMapping.same:(Lnet/minecraft/client/KeyMapping;)Z
114: invokevirtual net/minecraft/client/KeyMapping.hasKeyModifierConflict:(Lnet/minecraft/client/KeyMapping;)Z
```

几个容易写错的地方：

- `hasKeyModifierConflict` 在 `KeyMapping` 的成员表里**不存在**，它只是
  `IForgeKeyMapping` / `IKeyMappingExtension` 的 `default` 方法；字节码靠 JVMS 5.4.3.3
  沿超接口解析，所以接收者类型仍然是 `KeyMapping`。**`@Redirect` 的 target 必须写
  `Lnet/minecraft/client/KeyMapping;hasKeyModifierConflict(...)Z`**，写成
  `IForgeKeyMapping` / `IKeyMappingExtension` 会匹配不到。
- 这两处调用**全游戏各仅一次**（都在 `refreshEntry` 内），因此注入不会误伤。
- 1.20.1 生产环境（SRG）下 `same` 变成 `m_90850_`（由 refmap 重映射），
  `hasKeyModifierConflict` 不在映射表中，原样透传；类名 `net/minecraft/client/KeyMapping`
  在开发与生产环境一致。
- NeoForge 1.21.1 运行时就是 Mojmap，**不需要 refmap**。

### 1.3 两个版本的差异

| | 1.20.1 Forge | 1.21.1 NeoForge |
|---|---|---|
| 目标类 | `net.minecraft.client.gui.screens.controls.KeyBindsList$KeyEntry` | `net.minecraft.client.gui.screens.options.controls.KeyBindsList$KeyEntry` |
| 冲突字段 | `private boolean hasCollision` | 同 |
| 方法 | `protected void refreshEntry()V` | 同 |
| 判据 owner | `net/minecraft/client/KeyMapping` | 同 |
| refmap | 需要（MixinGradle 生成） | 不需要 |
| 扩展接口 | `net.minecraftforge.client.extensions.IForgeKeyMapping` | `net.neoforged.neoforge.client.extensions.IKeyMappingExtension` |
| `getCategory()` | `()Ljava/lang/String;` | 同 |

`KeyMapping#getCategory()` 与 `Options.keyMappings` 在两个版本中签名完全一致，
所以共享源码里的 MC 桥接层（`client/KeyMappingLayers.java`）可以一份通吃。

## 2. 本 mod 的做法

### 2.1 注入方式

只替换 `refreshEntry()` 里的两个判据，不重写整个方法：

```java
@Redirect(method = "refreshEntry()V",
          at = @At(value = "INVOKE",
                   target = "Lnet/minecraft/client/KeyMapping;same(Lnet/minecraft/client/KeyMapping;)Z"),
          require = 0)
private boolean keybindlayers$sameWithinLayer(KeyMapping self, KeyMapping other) {
    return self.same(other) && KeyMappingLayers.reportConflict(self, other);
}
```

`hasKeyModifierConflict` 同理。这样按钮文本、tooltip、选中行高亮等全部保持原版逻辑，
只有「是否算冲突」这一个布尔值被过滤。

`require = 0` 的取舍：注入点因原版或加载器更新而移位时，mod 静默失效而不是让客户端崩溃。
开发期想强制暴露漂移，把 `require` 改成 1，或把 `keybindlayers.mixins.json` 里的
`injectors.defaultRequire` 改成 1。

#### 2.1.1 两个已处理的注解细节

- 目标类用**类字面量** `@Mixin(KeyBindsList.KeyEntry.class)`，而不是 `targets = "..."` 字符串：
  编译期就能发现包名写错（1.21.1 把 `KeyBindsList` 挪进了 `options.controls`），
  同时消除注解处理器的 "Mixin target ... is public and should be specified in value" 警告。
- `hasKeyModifierConflict` 的 `@At` 显式写 `remap = false`。它是 `IForgeKeyMapping` /
  `IKeyMappingExtension` 上的 default 方法，**不在 SRG 映射表里**，不写的话 1.20.1 构建时
  注解处理器会警告 "Unable to locate method mapping"（功能上无害，但噪音会掩盖真问题）。
  `same` 保持默认 `remap = true`。

#### 2.1.2 1.20.1 产物中已核实的 refmap

`keybindlayers-forge-1.20.1-1.0.0.jar` 内的 `keybindlayers.refmap.json`（构建产物实测）：

```json
{
  "mappings": {
    "caibai_fish/keybindlayers/forge/mixin/KeyBindsListKeyEntryMixin": {
      "Lnet/minecraft/client/KeyMapping;same(Lnet/minecraft/client/KeyMapping;)Z": "Lnet/minecraft/client/KeyMapping;m_90850_(Lnet/minecraft/client/KeyMapping;)Z",
      "refreshEntry()V": "Lnet/minecraft/client/gui/screens/controls/KeyBindsList$KeyEntry;m_264257_()V",
      "net.minecraft.client.gui.screens.controls.KeyBindsList$KeyEntry": "net/minecraft/client/gui/screens/controls/KeyBindsList$KeyEntry"
    }
  },
  "data": { "searge": { "...": "同上（生产环境实际使用这一段）" } }
}
```

`same` → `m_90850_` 与 DESIGN.md §1.2 的 SRG 反汇编结果一致；`hasKeyModifierConflict`
**不在** refmap 中，运行时按原名匹配，与 SRG jar 里它未被重命名的事实一致。

### 2.2 层级模型

`KeyLayer`（id + 翻译键 + 深度序），内置 8 个层级：

| id | 含义 | 典型按键 |
|---|---|---|
| `system` | 系统/全局 | 截图、视角、全屏、字幕 |
| `debug` | 调试 | F3 系列 |
| `gameplay` | 游戏内操作 | 移动、跳跃、攻击、使用、丢出 |
| `vehicle` | 载具/骑乘 | 原版没有，留给载具类 mod |
| `inventory` | 物品栏/容器 | 打开背包、丢弃、创造模式快捷栏 |
| `social` | 社交 | 聊天、玩家列表 |
| `interface` | 界面 | 只在 GUI 中生效的按键 |
| `mods` | 未识别分类的兜底共享层 | 仅 `shared_layer` 策略使用 |

深度序（`order`）只用于排序展示，不参与判定；未来若要做「按键分发也按层级过滤」，
它可以作为优先级依据。

### 2.3 判定算法

`LayerRules#reportConflict`：**仅当两个绑定属于同一层级时返回 true**（原版判定为冲突仍是前提）。

层级解析顺序（先命中者优先）：

1. `mappings` 单键覆盖：`分类|按键名` → 层级
2. `categories` 分类覆盖（配置或其它 mod 通过 API 指定）
3. 内置默认分层（`KeyLayers.DEFAULT_CATEGORY_LAYERS`）：原版 7 个分类按需求被压成
   **两层**——`movement`/`gameplay`/`misc` → `gameplay`，
   `ui`/`inventory`/`creative`/`multiplayer` → `interface`
4. `unknownCategoryPolicy` 兜底：`own_layer` 生成 `category:<分类名>` 的临时层级 / `shared_layer` → `mods` / `strict` → `system`

`enabled = false` 时 `reportConflict` 恒为 `true`，即完全退回原版。

### 2.3.1 修饰键维度：组合键与单键不算冲突

层级之外还有一维要判：**修饰键**（组合键前缀）。原版 `KeyMapping#same()` 在 `IN_GAME`
上下文下会把 `W` 与 `Shift+W` 判为冲突——两者 `getKey()` 相同，且一方修饰键是 `NONE`：

```java
if (getKey().equals(b.getKey()))
    return m == om || (conflicts(IN_GAME) && (m == NONE || om == NONE));
```

但按 `W` 并不会触发 `Shift+W`，玩家把两者绑到不同功能通常是有意的组合。因此
`KeyMappingLayers.reportConflict` 额外要求两侧**修饰键相同**，由
`LayerRules.separateModifierCombinations()` 控制（配置项 `separateModifierCombinations`，
默认 `true`；设 `false` 退回原版）。它同样只影响显示判定，不影响触发。

跨平台细节：两个加载器的 `KeyModifier` 枚举在不同包，而桥接层在共用 `shared/` 里，
因此走 `client/KeyModifiers` 注入的方法引用（见 HANDOFF 的「组合键与单键的冲突修复」）。

### 2.4 为什么没改按键触发逻辑

本次范围限定为「消除设置界面的假冲突显示」，不改按键分发。理由：

- 原版按键触发路径（`KeyMapping#consumeClick` / `KeyMappingLookup` / 各界面轮询）
  分散且被大量 mod 依赖，改动风险远高于收益；
- 真正的「同键同时响应」在现代加载器里已经有 `KeyConflictContext`（Forge/NeoForge）
  或 `KeyModifier` 机制处理，本 mod 的层级只解决「显示」这一层。

若后续要做层级感知的分发，需同时处理 `Options.keyMappings` 遍历、
`KeyMappingLookup.getAll` 与各屏幕的 `keyPressed` 分支，建议单开一轮设计。

## 3. 后续可扩展方向

- ~~在「控制」界面为每行显示层级名（语言文件已备好 `keybindlayers.layer.*`）~~ ——
  1.21.1 曾实现过，后按用户要求**去掉**：标签栏与分类标题已经表达了层级，行内再标一次是冗余。
- ~~提供游戏内配置界面（当前只有 JSON 配置）~~ —— 已在 1.21.1 实现为
  「模组列表 → Config」的层级管理界面，见 §4.4。
- 层级感知的按键分发（见 2.4）。
- 支持 Fabric（1.20.1 / 1.21.1）：核心与桥接层可直接复用，只需新增 Fabric 工程与
  `KeyBindsList` 的 mixin（Fabric 用的是 Yarn 映射，需另写映射名）。
- 1.21.2+ 适配：`category` 从 `String` 变成 `KeyMapping.Category` 注册对象，
  `LayerRules` 的字符串入口需要新增一个按 `Category` 解析的重载。

## 4. 1.21.1 界面重做（第二轮，2026-01 起）

> 本轮**只做 1.21.1 NeoForge**。1.20.1 的原版冲突检测改造（§2）保持不变。

### 4.1 目标

1. 自建「按键设置」界面接管「选项 → 控制 → 按键设置」，不再用 Mixin 给原版列表打补丁；
2. 顶部按操作层级生成**标签栏**，点标签只看该层级的按键；
3. ~~每行在按键名右侧用灰色小字标出所属层级~~ —— 后按用户要求去掉，
   改成在层级内按 mod 分类分段（两级分组，见 §4.7）；
4. 新增可从「模组列表 → Config」进入的**层级管理界面**，含「整合包作者模式」开关，
   可创建 / 修改 / 删除层级。

### 4.2 入口注入点（javap 核实）

1.21.1 的入口不是 `OptionsScreen` 直连 `KeyBindsScreen`，中间有 `ControlsScreen`：

```
OptionsScreen.lambda$init$3()            → new ControlsScreen(this, options)
ControlsScreen.addOptions()              → 「按键设置」按钮（文本 controls.keybinds）
ControlsScreen.lambda$addOptions$1(Button)
     4: new           KeyBindsScreen
    13: invokespecial KeyBindsScreen."<init>"(Screen, Options)V
    16: invokevirtual Minecraft.setScreen(Screen)V      ← 注入这里
```

`ControlsScreenMixin` 用 `@ModifyArg` 替换 `setScreen` 的实参。
**没有**用 `@Redirect` 掉 `KeyBindsScreen.<init>`：构造器调用作用在 `new` 出来的未初始化实例上，
Redirect 无法安全替代。改 `setScreen` 入参等价，且不影响同一类里「鼠标设置」分支
（它在另一个 lambda 方法 `lambda$addOptions$0` 里）。

### 4.3 为什么必须自建列表

原版以下成员的可见性使「继承 + 覆写」不可行（javap 核实）：

| 成员 | 修饰符 | 后果 |
|---|---|---|
| `KeyBindsList$KeyEntry` 构造器 | 包私有 | 工程外无法实例化 |
| `KeyBindsList$CategoryEntry` 构造器 | 包私有 | 同上 |
| `KeyBindsList$Entry#refreshEntry()` | 包私有抽象 | 工程外无法实现该抽象方法 |
| `KeyBindsScreen.keyBindsList` / `resetButton` | private | 子类拿不到；而父类 `render()` 无条件读 `resetButton` |

最后一条尤其致命：即使继承 `KeyBindsScreen` 并覆写 `addContents`，
`render()` 仍会读 private 的 `resetButton` 而 NPE，且 Java 无法跳过父类直接调用
`OptionsSubScreen.render`。因此 `LayerKeyBindsScreen` 直接继承 `OptionsSubScreen`，
`LayerKeyBindsList` 自己继承 `ContainerObjectSelectionList`。

几何与交互按原版 1:1 复刻（javap 逐行核对）：条目高 20、行宽 340、
「改键」按钮 75x20、「重置」按钮 50x20、按钮位置由 `getScrollbarPosition()` 向左反推、
冲突红条为 `changeButton.getX() - 6` 起、宽 3px、`0xFFFF0000`、`top - 1` 到 `top + height`。

### 4.4 标签栏与层级管理界面

- 标签 = `全部` + 各层级。`own_layer` 兜底生成的 `category:xxx` 与 mod 自定义作用域产生的
  `context:xxx` 也参与，但**标签名取所属 mod 的显示名**（把层级 id 按 `.` / `$` 切段去问
  `ModList`），因此同一个 mod 的多个层级会并进同一个标签；原版分类与取不到 mod 名的层级
  退回分类的本地化名。合并只发生在显示层，`layerOf` 仍返回各自层级，同标签下的层级互不冲突。
- 标签栏**固定一行**：放不下时在标签栏上滚滚轮横向滚动，两端用 `<` `>` 提示，被截断的
  标签照常渲染——截断本身就是「后面还有」的提示，全藏起来反而没法判断滚到哪儿。
- 层级管理界面通过 `IConfigScreenFactory` 注册，注册点是新增的客户端入口类
  `KeybindLayersNeoForgeClient`（`@Mod(dist = Dist.CLIENT)`）：
  `Screen` 是 `@OnlyIn(CLIENT)`，写在双端入口里会让专用服务器在
  `RuntimeDistCleaner` 阶段直接抛异常。
- 一个 mod 只能有一个配置界面（`ModContainer#registerExtensionPoint` 内部是
  `IdentityHashMap.put`，同 key 覆盖），因此子页必须在该屏幕内部导航。

### 4.5 数据层扩展

| 新增 | 作用 |
|---|---|
| `LayerDefinition` | 可序列化的层级定义（id / displayName / order） |
| `LayerConfig#layers` | 显式定义过的层级；内置层级不写也在，写了只取显示名 |
| `LayerConfig#packAuthorMode` | 「整合包作者模式」开关，持久化到 JSON |
| `KeyLayers#applyDefinitions` | 按配置注册 / 覆盖 / 清理层级（内置层深度不可覆盖） |
| `KeyLayers#isBuiltin` / `unregister` / `setDisplayName` | 内置保护、删除、显示名 |
| `LayerManager` | 创建 / 修改 / 删除的校验与数据改动（纯逻辑，不碰 UI） |
| `LayerRules#removeReferencesTo` | 删除层级后清理指向它的分类 / 单键覆盖，使其回退 |
| `LayerRules#clearConfigAssignments` | **只清配置来源的赋值**，保留其它 mod 通过 API 声明的归属 |

`clearConfigAssignments` 是这一轮补的一个真实缺陷修复：原来的 `apply()` 调
`clearAssignments()` 全清，玩家在层级管理界面每保存一次，就会把别的 mod 在初始化阶段
通过 `KeybindLayersApi` 声明的归属抹掉。现在配置来源与 API 来源分开记账。

### 4.6 已修与已知差异

- ~~原版 20ms 热键保护失效~~ —— **已修**（2026-10-10）。原版 `KeyboardHandler#keyPress` 里
  「刚改完键 20ms 内不响应 F11/F2」的判据是 `screen instanceof KeyBindsScreen`，而本界面
  不是它的子类（那个类没法子类化，见 §4.3），于是保护失效：等待改键时按 F11 会连带切全屏。

  修法见 `KeyboardHandlerMixin`。这里**返工了两次**，三个坑都值得记：

  1. **`INSTANCEOF` 不是合法注入点**。本意是用 MixinExtras 的 `@ModifyExpressionValue`
     改那个 `instanceof` 的结果，实测直接崩在 `Initializing game`：
     `INSTANCEOF is not a valid injection point specifier`。

  2. **不能只拦「切全屏」那个动作**。第二版改成 `@Redirect` 拦住
     `Window#toggleFullScreen` 与 `Screenshot#grab`，确实不切屏了；但**那段分支末尾的
     `return` 照样执行**——按键被吞掉、根本没送到界面，界面从没记录到按下的 F11，
     最终把它绑成了「未设置」（用户实测报回）。

     **正解是拦那两个 `KeyMapping#matches` 调用**：等待改键时返回 false，整段分支
     （含 `return`）就被跳过，控制流继续走到「把按键交给 Screen」的地方。于是 F11 会像
     普通键一样进入改键流程，再由 `LayerKeyBindsScreen#isReservedByLockedKey` 判定它被
     `key.fullscreen` 占用并拒绝、给出提示。

  3. **`@At` 的 target 描述符写法**：方法用括号 `Lowner;name(desc)ret`，**冒号只给字段**
     （`Lowner;field:Type`）。写成 `matches:(II)Z` 会报
     `InvalidMemberDescriptorException: Invalid name: matches:`。

  **判据不能照抄原版**：原版的 `lastKeySelection` 只在**选中按键时**更新一次（javap：
  `putfield selectedKey` 紧跟 `putfield lastKeySelection`，同一个方法里），所以那个
  「20ms 窗口」窄得几乎没用——**等待改键期间按 F11 照样会切全屏**，原版自身也有这个毛病。
  正确的判据是「是否正在等待玩家按键」（`LayerKeyBindsScreen#isAwaitingRebind`），
  外加原来那个 20ms 窗口作为补充。

  实测（完全走真实按键通路 `KeyboardHandler#keyPress`，并把 `lastKeySelection` 故意设成
  10 秒前以复现真实情况）：绑空闲键 `K` → 成功；绑 `F11` → **原绑定不变、没变成「未设置」、
  也没切全屏**，屏幕底部出现红字「无法设置成该按键：已被「切换全屏显示」占用」。

  两个注入点都写了 `require = 1`，注入失败会明确崩溃而不是静默失效——上面第 1、3 个坑
  就是被它揪出来的。

- `require = 0` 依旧用于界面接管那两处：`lambda$addOptions$1` 由 javac 按出现顺序命名，
  原版重编译后可能变化，届时静默退回原版界面而不崩溃。

### 4.7 按键声明的作用域与两级分组

**作用域来源**：NeoForge 的 `IKeyConflictContext`（`IN_GAME` / `GUI`）是 mod 作者写下的
「这个键在什么场合生效」，正是本 mod 的操作层级。层级解析因此成为三层优先级：

1. 配置里的显式条目（`categories` / `mappings`，含内置适配表），由
   `LayerRules#hasExplicitAssignment` 判断；
2. 按键声明的作用域（`client/KeyContexts`，注入式；1.21.1 在
   `KeybindLayersNeoForgeClient` 注入，配置项 `useDeclaredKeyContext`）；
3. 未知分类策略。

**自定义 context 各自成层**：很多 mod 自己实现 `IKeyConflictContext`，判断的是「当前屏幕
是不是我自己的界面」。这类读不出标准值，但它本身就是作者写下的作用域声明——用**它的类型名**
当层级标识即可（`context:<类全名>`，见 `KeyMappingLayers#CONTEXT_LAYER_PREFIX`）：
同一个界面的键同层（彼此仍报冲突），不同 mod 的界面天然分开。
**不能把它们压进同一个 `interface` 层**：不同 mod 的界面永远不会同时打开，压在一起就是
假冲突——实测反例：JEI 的悬停查配方与 Mekanism 的模块详情绑到同一个键会被标红。

**有一类自定义 context 要并进游戏内**：条件只是「装备/手持某物」的那些，游戏内随时可用
（如 Sophisticated Backpacks 的「打开背包」）。判定办法：在**没有界面**时调一次
`isActive()`——这类实现只看 `GUI.isActive()` 与
`screen instanceof AbstractContainerScreen`，此时返回 true；界面类返回 false。

采样器是 `neoforge/client/InGameContextSampler`，**两个时机**：

1. 进入世界后、无界面时顺带采；
2. 打开按键界面时由 `LayerKeyBindsScreen#init` 调 `sampleNow()` 补采——它临时把
   `Minecraft.screen` 置空再恢复。少了这一步，玩家只要没进过世界就看不到正确分层。

结果记在 `client/KeyContexts`，注入的解析器在成层前先查。

**剩下读不出声明的才手工**：`UNIVERSAL` 是 NeoForge 的默认值（等于没写）。一个分类里只要
有一部分按键没有声明，它们就会掉进 `category:` 临时层、把同一个 mod 的键拆散，所以
JourneyMap（同组混 `UNIVERSAL`）与 Xaero's（全 `UNIVERSAL`）留在
`DefaultAdaptations` 的手工表里。

**两级分组**：`LayerKeyBindsList` 按「层级 → 分类」组织列表。层级标题居中、白色；
分类标题左对齐、灰色。层内只有一个分类时不显示分类标题——否则「其它」那种
一层一分类的标签里，标题会与层级标题重复。
