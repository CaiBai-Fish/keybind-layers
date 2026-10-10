# 工作区约定：Keybind Layers

面向在本工作区里继续开发的 AI 会话。**先读 [`docs/HANDOFF.md`](docs/HANDOFF.md)**（当前进度、
待验证项、已知风险），再读 [`docs/DESIGN.md`](docs/DESIGN.md)（原版机制与字节码依据）。

## 项目一句话

替换 Minecraft 原版的按键冲突检测，让**不同操作层级的同键不再在「控制」设置界面显示为冲突**；
同层级内的同键仍然报冲突。1.21.1 侧还重做了「控制」界面本身（层级标签栏），并新增层级管理界面。

## 范围（用户已确认，不要擅自扩大）

- 目标：**1.20.1 Forge** 与 **1.21.1 NeoForge**，两个独立 Gradle 工程。
- 「层级」由分组决定：同一层级内才算冲突。
- **只改冲突显示判定**，不在没有明确要求的情况下改按键的实际触发/分发逻辑。
- **1.21.1 专属的第二轮范围**（用户已明确要求，1.20.1 本轮不跟进）：
  - 自建层级感知的按键界面接管「选项 → 控制」入口；
  - 顶部按层级生成标签栏切换内容，层级内再按 mod 分类分段；
  - 新增从「模组列表 → Config」进入的层级管理界面，含「整合包作者」开关，
    可创建 / 修改 / 删除层级，删除后引用回退到未知分类策略。

## 结构

- `shared/` —— 两个工程共用的源码（`core/` 纯逻辑、`api/` 对外 API、`client/` MC 桥接与配置管理）。
- `forge-1.20.1/`、`neoforge-1.21.1/` —— 各自独立的 Gradle 构建。
- `neoforge-1.21.1/src/main/java/.../neoforge/client/` —— **1.21.1 专属的界面代码**
  （层级按键界面、层级管理界面）。这些类用到了 1.20.1 不存在的原版 API，所以放在工程内而非 `shared/`。
- `shared/resources/assets/keybindlayers/lang/` —— 两个平台共用的语言文件。

## 构建与验证

```powershell
cd "E:\mod-sources\Keybind Layers\neoforge-1.21.1"; .\gradlew.bat build
cd "E:\mod-sources\Keybind Layers\forge-1.20.1";    .\gradlew.bat build
```

改完代码**必须至少构建一次**；改 Mixin 或界面代码必须实机 `runClient` 打开「选项 → 控制」确认行为。

## 代码约定

- `shared/` 同时被 Java 17 与 Java 21 编译，**只能用 Java 17 语法**。
- 平台差异只允许出现在 `*/mixin/`、`*KeybindLayers*.java` 入口类，以及
  `neoforge-1.21.1/.../neoforge/client/`（1.21.1 专属界面）里；其余代码一律共享。
- `shared/` 必须能被 1.20.1 工程编译通过，**不要在里面 import 1.21.1 才有的原版类**
  （例如 `screens.options.controls.*`、`KeyMapping.Category`）。
- 中文注释，面向用户的文本走语言文件（`shared/resources/assets/keybindlayers/lang/`）。
- `.properties` 是 ISO-8859-1，只写 ASCII。

## 不要在不知情的情况下踩的坑

- `@Redirect` 的 owner 必须是 `Lnet/minecraft/client/KeyMapping;`——
  `hasKeyModifierConflict` 虽然定义在 Forge/NeoForge 的扩展接口上，字节码接收者却是 `KeyMapping`。
- 1.20.1 生产环境需要 refmap（MixinGradle 生成），1.21.1 不需要。
- 不要把 `forge-1.20.1` 的 Gradle 升到 9.x（ForgeGradle 6 未验证）。
- 接管界面的两个 Mixin 用 `require = 0`（注入失败静默降级）；怀疑没生效时先改成 1 排查。
  `KeyboardHandlerMixin` 的两个注入点用的是 `require = 1`，失败会明确崩溃而不是静默失效。
- **不要复用原版 `KeyBindsList$KeyEntry` / `KeyBindsList$CategoryEntry`**：它们的构造器是
  包私有（package-private），工程外无法实例化，自建界面必须自己写
  `ContainerObjectSelectionList.Entry` 子类。
- 配置重载只能调用 `LayerRules.clearConfigAssignments()`，**不要用 `clearAssignments()`**：
  后者会顺手清掉其它 mod 通过 API 声明的归属。
- Forge 与 NeoForge 的 `KeyModifier` 枚举在**不同包**，`shared/` 里不能 import 任何一个。
  需要读修饰键（组合键前缀）时走 `client/KeyModifiers`：它持有
  `Function<KeyMapping, Object>`，由 `KeyMappingLayers` 静态块用
  `KeyMapping::getKeyModifier` 注入（两边方法同名、返回类型擦除后都是 `Object`）。
- 原版会把 `W` 与 `Shift+W` 判为冲突（`KeyMapping#same` 在 `IN_GAME` 上下文下的语义），
  本 mod 默认把它们分开看待；相关开关是 `LayerRules.separateModifierCombinations()`。
- 按键「在什么场合生效」可以读 NeoForge 的 `IKeyConflictContext`（`IN_GAME` / `GUI`），
  但**默认值是 `UNIVERSAL`（等于没有信息）**，实测只有约六成 mod 按键声明得完整，
  自定义 context 也读不出来。读取走 `client/KeyContexts`（注入式；1.21.1 在
  `KeybindLayersNeoForgeClient` 注入，1.20.1 未接入即自动退回）。
- 自定义 `IKeyConflictContext` 要用**它的类型名当层级标识**（`context:<类全名>`），
  别把不同 mod 的界面按键压进同一个 `interface`：不同 mod 的界面永远不会同时打开，
  压一起就是假冲突。JEI 就是这么处理的，实测它与 Mekanism 的界面键绑同一个键不再报冲突。
- 但 `UNIVERSAL`（NeoForge 默认值）等于没有声明：一个分类里只要有一部分按键没有声明，
  它们就会掉进 `category:` 临时层、把同一个 mod 的键拆散，所以 JourneyMap 与 Xaero's
  仍留在 `DefaultAdaptations` 手工表里。
- 自定义 context 里有一类**该并进游戏内**：条件只是「装备/手持某物」的（如 SB 的
  `BackpackKeyConflictContext`）。判定办法是**在没有界面时**调它的 `isActive()`——
  这类实现只看 `GUI.isActive()` 与 `screen instanceof AbstractContainerScreen`，
  此时返回 true；界面类返回 false。采样在 `neoforge/client/InGameContextSampler`，
  结果记在 `client/KeyContexts`。**采样有两个时机**：进世界后顺带采，
  以及打开按键界面时用 `InGameContextSampler#sampleNow()` 临时置空 `Minecraft.screen`
  补采——少了后者，玩家不进世界就看不到正确分层（用户实测踩过）。
- 标签栏固定一行、滚轮横向滚动；**部分露出的标签必须照常渲染**，不要过滤成"只显示
  完整标签"——那样就看不出滚到哪儿了（用户明确要求过）。
- 标签名取**所属 mod 的显示名**（`LayerKeyBindsScreen#modDisplayNameOf`：把层级 id 按
  `.` / `$` 切段逐个问 `ModList`），同名层级（即同一个 mod）**并进一个标签**。
  别拿"该层某个按键的分类名"当标签名——JEI 的一个作用域横跨多个细分类，那样会得到
  「JEI（鼠标悬停）」这种以偏概全的名字，而且同一 mod 会裂成多个标签。
  合并只影响显示，判定仍按各自层级。
- 部分按键**硬编码不出现在设置界面里**（快捷栏 `1`-`9`、全屏 `F11`，清单在
  `core/LockedKeybinds`）：`LayerKeyBindsList#rebuild` 在分组**之前**就滤掉它们，
  `LayerKeyBindsScreen#hasMappingsFor` 也要跳过，否则会留下空的分类/层级标题或空标签。
  用户明确要求"直接写死"，**不要**改成配置项；也**不要**退回成"置灰"——用户要的是不显示。
- 原版按键**默认只分两层**：`movement`/`gameplay`/`misc` → `gameplay`，
  `ui`/`inventory`/`creative`/`multiplayer` → `interface`，映射写在
  `KeyLayers.DEFAULT_CATEGORY_LAYERS`；`DefaultAdaptations` 只留 3 条与分类不一致的单键覆盖。
  内置的 `system`/`inventory`/`social` 层级仍然存在（`SYSTEM` 还是 `category == null` 与
  `STRICT` 策略的兜底），**别把它们删掉**，只是默认没有按键落上去。
- 原版「刚改完键 20ms 内不响应 F11/F2」的保护判据写死了
  `screen instanceof KeyBindsScreen`，对自建界面无效。**别用 `INSTANCEOF` 当注入点**：
  当前 Mixin 版本不支持，会在 `Initializing game` 直接崩
  （`InvalidInjectionException: INSTANCEOF is not a valid injection point specifier`）。
  `KeyboardHandlerMixin` 的做法是 `@Redirect` 拦住 `Window#toggleFullScreen` 与
  `Screenshot#grab` 这两个**会被误触发的动作**，并给注入点写 `require = 1` 让失败显形。
  **守卫判据别照抄原版的 20ms**：原版 `lastKeySelection` 只在选中按键时更新一次，那个窗口
  窄得没用，等待改键期间按 F11 照样切全屏（用户实测反馈过）。要用
  `LayerKeyBindsScreen#isAwaitingRebind()`（`selectedKey != null`）。
- 拦「快捷键被误触发」要拦**判断本身**（`KeyMapping#matches`），**别拦它触发的动作**
  （`toggleFullScreen` / `Screenshot#grab`）：动作虽然拦住了，但那段分支末尾的 `return`
  照旧执行，按键会被吞掉、根本送不到界面——表现为改键时按 F11 被绑成「未设置」。
- `@At` 的 target 描述符：**方法用括号** `Lowner;name(desc)ret`，**冒号只给字段**
  （`Lowner;field:Type`）。写成 `matches:(II)Z` 会报
  `InvalidMemberDescriptorException: Invalid name: matches:`。
- 被锁定按键占用的键位要在**改键阶段**拦截（`LayerKeyBindsScreen#isReservedByLockedKey`），
  否则玩家会看到「冲突」红字却找不到冲突对象——那些键不显示，但仍在 `keyMappings` 里。
