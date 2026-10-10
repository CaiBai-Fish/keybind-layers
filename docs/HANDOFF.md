# 交接清单（HANDOFF）

> 本文件写给下一个接手这个工作区的人 / AI 会话。先读本文件，再读
> [DESIGN.md](DESIGN.md)（设计依据，含 javap 字节码证据）与
> [../README.md](../README.md)（使用与构建）。

## 1. 这是什么

`Keybind Layers`：替换原版按键冲突检测，让**不同操作层级的同键不再显示为冲突**。
用户已确认的三条范围决策：

1. 目标平台：**1.20.1 Forge + 1.21.1 NeoForge**（多加载器，各一个独立 Gradle 工程）；
2. 「不同操作层级」由**分组**决定：同一层级内才算冲突；
3. 本轮**只改冲突显示判定**，不改按键实际触发/分发。

## 2. 当前状态

### 已完成

- 工作区骨架：`shared/` 共享源码 + `forge-1.20.1/`、`neoforge-1.21.1/` 两个独立 Gradle 工程
  （各自带 wrapper：8.8 / 9.3.0，wrapper jar 从 `Meds and Herbs`、`Towards the Stars` 复制）。
- 共享核心（不依赖任何加载器与 MC 类）：`KeyLayer`、`KeyLayers`、`UnknownCategoryPolicy`、
  `LayerRules`、`LayerConfig`、`LayerConfigIO`。
- 对外 API：`api/KeybindLayersApi`（纯 Java，无 MC 类型）。
- MC 桥接与客户端初始化：`client/KeyMappingLayers`、`client/KeybindLayersClient`。
- 两个平台各一份 Mixin（`KeyBindsListKeyEntryMixin`）与 mod 入口类。
- 资源：`mods.toml` / `neoforge.mods.toml`（含 `[[mixins]]` 段）、`*.mixins.json`、
  `pack.mcmeta`、`assets/keybindlayers/lang/{en_us,zh_cn}.json`。
- 注入点已用 `javap` 逐字节核实（见 DESIGN.md §1.2），不是推测。

### 第二轮：1.21.1 界面重做（进行中）

用户已确认的新范围（**只做 1.21.1**）：自建按键界面接管「选项 → 控制 → 按键设置」、
顶部按层级生成标签栏、层级内再按 mod 分类分段、新增「模组列表 → Config」的层级管理界面
（含「整合包作者模式」开关，可创建 / 修改 / 删除层级）。设计与注入点依据见
[DESIGN.md](DESIGN.md) §4。

已写好的代码：

- 数据层：`LayerDefinition`、`LayerConfig#layers` / `#packAuthorMode`、
  `KeyLayers#applyDefinitions/isBuiltin/unregister/setDisplayName`、`LayerManager`、
  `LayerRules#removeReferencesTo` / `#clearConfigAssignments`、
  `KeybindLayersClient#config` / `#saveAndApply`。
- 桥接层：`KeyMappingLayers#conflictsOf` / `#groupByLayer`、`LayerNames`。
- 1.21.1 界面（`neoforge-1.21.1/src/main/java/.../neoforge/client/`）：
  `LayerKeyBindsScreen`（标签栏 + 与原版对齐的按键捕获）、
  `LayerKeyBindsList`（层级 → 分类两级分组 + 冲突红条）、
  `LayerManagerScreen`、`LayerEditScreen`、`KeybindLayersNeoForgeClient`。
- Mixin：`ControlsScreenMixin`（`@ModifyArg` 替换 `setScreen` 的实参）。
- 语言文件补齐 `keybindlayers.tab.*` 与 `keybindlayers.config.*`。

状态：数据层与界面代码**已编译通过**（`compileJava` 与 `build` 均 `BUILD SUCCESSFUL`），
但**界面行为尚未实机确认**——见下面的待验证项。

### 第三轮：默认分层适配（已完成）

目标：优化原版分类归属，并为若干主流 mod 提供默认适配。适配写进
`LayerConfig.defaults()` 生成的配置文件（玩家可覆盖），**不是**硬编码进判定逻辑。

实现：新增 `DefaultAdaptations`（`shared/.../core/`），由 `LayerConfig.defaults()` 调用。

- **原版 6 条按键级重新归层**：`misc` 里的 `key.togglePerspective` / `key.smoothCamera` /
  `key.spectatorOutlines` → `gameplay`（游戏内随时生效，会和移动、攻击同时响应），
  `key.advancements` → `interface`（打开的是 GUI）；`inventory` 里的 `key.drop` /
  `key.swapOffhand` → `gameplay`。
- **mod 分类适配**：JEI（8 个细分类 → `interface`）、JourneyMap（小地图组 → `gameplay`、
  全屏地图组 → `interface`）、Xaero's Minimap（→ `gameplay`，另有 3 个打开界面的键 →
  `interface`）、Xaero's World Map（→ `interface`）、Mekanism（→ `gameplay`，另有 3 个
  说明/调整界面的键 → `interface`）。

**数据来源（重要，别再用猜的）**：mod 的分类字符串毫无规范——Mekanism 用
`constants.mekanism.mod_name`，Xaero's 直接用人可读的 `Xaero's Minimap`（带撇号），
JEI 拆成 8 个 `jei.key.category.*`；按键名前缀也不统一（Xaero's 是 `gui.xaero_*` 而不是
`key.*`）。所以从语言文件推断是行不通的。做法是：把 mod 装进 `run/mods`，
用一次性探针打印 `getName()` / `getCategory()` / 解析出的层级。

验证结果（装了 Xaero's Minimap + World Map、JourneyMap、JEI、Mekanism，共 131 个按键）：

| 层级 | 按键数 | 主要来源 |
|---|---|---|
| system | 2 | 截图、全屏 |
| gameplay | 54 | 原版移动/攻击 + JourneyMap 小地图组 + Xaero's 小地图 + Mekanism 模块键 |
| inventory | 12 | 快捷栏、打开背包 |
| social | 4 | 聊天、玩家列表 |
| interface | 59 | JEI 全部 + Xaero's 世界地图 + JourneyMap 全屏组 + 进度 |

原版 6 条逐条核对通过；界面截图确认标签栏新增「界面」标签，「系统」层只剩截图与全屏两项。

工具与残留：

- `tools/probe-mod-keybinds.ps1`：从 Modrinth 拉 mod jar 并提取语言文件里的按键名，
  用来快速摸清一个 mod 有哪些按键（分类字符串仍需实机确认）。
- `neoforge-1.21.1/run/mods/` 里留了 6 个测试 mod（Xaero's ×2、JourneyMap、JEI、
  MezzConfig、Mekanism）。不需要就删掉——配置里针对它们的条目会自然失效，无害。

### 组合键与单键的冲突修复（已完成）

**问题**（用户报告）：`W` 与 `Shift+W` 会被标红，但按 `W` 并不会触发 `Shift+W`，
玩家把两者绑到不同功能通常是有意的组合。

**根因**：这是**修饰键**维度的问题，与层级无关，所以原有的层级过滤管不到。原版
`KeyMapping#same()`（NeoForge 版）里：

```java
if (getKey().equals(b.getKey()))
    return m == om || (conflicts(IN_GAME) && (m == NONE || om == NONE));
```

两个键都是 `W`，修饰键一个是 `NONE` 一个是 `SHIFT`，而 `IN_GAME.conflicts(IN_GAME)` 为真、
`m == NONE` 也为真 → 返回 `true`。

**修复**：在 `KeyMappingLayers.reportConflict` 里加一层「修饰键必须相同」的判定，
由 `LayerRules.separateModifierCombinations()` 控制，配置项
`separateModifierCombinations` 默认 `true`，设为 `false` 即退回原版行为。
只影响冲突显示，不影响按键触发。两条判定路径（新界面的 `conflictsOf` 与旧
`KeyBindsListKeyEntryMixin`）都走 `reportConflict`，因此同时生效。

**跨平台处理（值得记住）**：Forge 与 NeoForge 的 `KeyModifier` 枚举位于不同包，
而 `KeyMappingLayers` 在共用 `shared/` 里、不能 import 任一版本。解决办法是新增
`client/KeyModifiers`，持有一个 `Function<KeyMapping, Object>`，在 `KeyMappingLayers`
的静态块里用 `KeyMapping::getKeyModifier` 注入——两边读取方法同名、返回类型擦除后
都是 `Object`，于是一份代码两个平台通用，既不用反射，也不用改平台入口类。
读取失败时返回 `null` 并**退回原版判定**（宁可按原版标红，也不漏报真冲突）。

**实测**（1.21.1，探针临时改键后自动恢复）：

| 场景 | 结果 |
|---|---|
| `Shift+W` vs `W` | `<none>`（不再冲突） |
| `W` vs `W` | 报冲突（`key.left`） |
| `Shift+W` vs `Shift+W` | 报冲突（`key.left`） |

1.20.1 也编译通过（仍是只改冲突显示，没有跟进界面重做）。

### 作用域自动分层 + 两级分组（已完成）

两项需求：列表在层级标签内再按 mod 分类；以及利用按键自己声明的生效作用域自动分层。

**1. 按键声明的作用域**

NeoForge 的 `IKeyConflictContext`（`IN_GAME` / `GUI`）就是 mod 作者写下的「这个键在什么
场合生效」，正是本 mod 说的操作层级。实测（装 JEI / JourneyMap / Mekanism / Xaero's，
97 个 mod 按键）**59 个有明确声明，约六成**。

实现：`client/KeyContexts`（注入式，跨平台写法与 `KeyModifiers` 相同），1.21.1 在
`KeybindLayersNeoForgeClient` 注入 `IN_GAME → gameplay`、`GUI → interface`；
1.20.1 未接入，`layerIdOf` 返回 null 即自动退回原逻辑。层级解析变成三层优先级：

1. 配置里的显式条目（`categories` / `mappings`，含内置适配表）——新增
   `LayerRules.hasExplicitAssignment` 判断；
2. 按键声明的作用域（配置项 `useDeclaredKeyContext`，默认 true）；
3. 未知分类策略。

**2. 实测带来的分流决策**（重要，别再想当然）：

| mod | 处理 | 原因 |
|---|---|---|
| Mekanism | **移出适配表**，交给自动判定 | 10 个键全部声明了标准作用域，且比手工准：`key.mekanism.module_tweaker` 手工版误判成 `interface`，实际是 `IN_GAME` |
| JEI | **移出适配表**，按自定义作用域各自成层 | 用的是 `JeiForgeKeyConflictContexts`，读不出标准值——但见下条 |
| JourneyMap | 留在适配表 | 同一分类里混着 `IN_GAME` 与 `UNIVERSAL`，只靠自动判定会把同组拆到两个层级 |
| Xaero's（两个） | 留在适配表 | 全部是 `UNIVERSAL`，没有任何声明 |

教训：**不能一刀切地"有 context 就交给自动"**——只要一个分类里有按键读不出声明，
它就会掉进 `category:` 临时层，把同一个 mod 的键拆散。

**2.1 自定义 context 各自成层**（用户提出的问题，很关键）

很多 mod 自己实现 `IKeyConflictContext`，判断的是「当前屏幕是不是我自己的界面」。
一开始 JEI 这类被手工压进了 `interface` 层，结果是：**不同 mod 的界面按键互报冲突**——
可它们的界面永远不会同时打开，这是本 mod 该消灭的假冲突。

修法：自定义 context 用**它的类型名**当层级标识（`context:<类全名>`，
见 `KeyMappingLayers.CONTEXT_LAYER_PREFIX`），首次遇到时即时注册、显示名取类的简单名。
于是同一个界面的键同层（彼此仍报冲突），不同 mod 的界面天然分开。

实测（把三个键都绑到 F8）：

```
key.jei.showRecipe    layer = context:mezz.jei.neoforge.input.JeiForgeKeyConflictContexts
key.jei.showUses      layer = 同上
key.mekanism.details  layer = interface

jei.showRecipe    conflicts -> key.jei.showUses     （同界面，该冲突）
mekanism.details  conflicts -> <none>               （跨 mod 界面，不再假冲突）
```

**2.2 「只要装备/手持物品」的并进游戏内**（用户指出）

Sophisticated Backpacks 的「打开背包」这类按键，条件只是装备/手持了某件物品，游戏进行中
随时可用，不该单开一个界面标签。反编译 `BackpackKeyConflictContext.isActive()`：

```java
if (!KeyConflictContext.GUI.isActive()) return true;     // 没有界面 → 活跃
return Minecraft.getInstance().screen instanceof AbstractContainerScreen;
```

它**只看有没有界面**，压根不看背包是否装备——所以在「没有界面」时调一次 `isActive()`，
返回 true 的就是这类作用域，并进 `gameplay`；返回 false 的（JEI、容器界面）保持独立层。

实现：`client/KeyContexts` 记录采样结果（`markInGameContext` / `isInGameContext`），
`neoforge/client/InGameContextSampler` 在「已进入世界且无界面」时采样，注入的解析器
在成层之前先查这个记录。

实测（15 个 mod，含 AE2 / SB / Iron's Spells）：

```
key.sophisticatedbackpacks.open_backpack | BackpackKeyConflictContext | true  → gameplay
key.sophisticatedcore.sort               | SophisticatedScreen...      | false → 独立层
key.jei.showRecipe                       | JeiForgeKeyConflictContexts | false → 独立层
```

**采样时机有两个**（早先只做了第一个，用户实测发现「没进世界就不生效」）：

1. **进入世界之后**、无界面时顺带采一次（`ClientTickEvent.Post`）；
2. **打开按键界面时补采一次**（`LayerKeyBindsScreen#init` 调
   `InGameContextSampler#sampleNow()`）——它临时把 `Minecraft.screen` 置空来模拟
   「没有界面」，同步跑完立刻恢复，所以玩家不必先进过一次世界。

实测：未进世界、直接打开「选项 → 控制 → 按键设置」，
`key.sophisticatedbackpacks.open_backpack` 已经是 `gameplay`。

**3. 两级分组**：`LayerKeyBindsList` 按「层级 → 分类」组织。层级标题居中白色、
分类标题左对齐灰色；**层内只有一个分类时不显示分类标题**，否则「其它」那种一层一分类的
标签里标题会重复。

**4. 标签按 mod 归并**（按用户要求，2026-10-10 两轮调整）：

- 第一轮：`category:xxx` 与 `context:xxx` 各自成标签（早先它们被聚合成一个「其它」标签）。
- 第二轮：**标签名改为所属 mod 的显示名**，同名（即同 mod）的层级**并进一个标签**。

理由：按"该层第一个按键的分类名"命名时，JEI 的 19 个按键分属多个细分类，标签会叫
「JEI（鼠标悬停）」这种以偏概全的名字；而且 Sophisticated Backpacks 的 `category:xxx` 层
（分类名「精妙背包」）与 `context:xxx` 层（mod 名）名字不同，会裂成两个标签。

现在统一：`LayerKeyBindsScreen#modDisplayNameOf` 把层级 id 按 `.` 与 `$` 切段逐个问
`ModList`，命中就拿 mod 显示名；原版分类切出来的段不是 modid，自然退回分类的本地化名。
`buildTabs` 再按标签名分组，同名的多个层级共用一个标签（`Tab#filter` 匹配组内所有层级 id）。

**判定不受影响**：合并只发生在标签与列表显示层，`layerOf` 仍返回各自的层级，
所以同标签下的不同层级互不冲突。

实测（15 个 mod）：标签变为 `全部 / 系统 / 游戏内操作 / 物品栏 / 社交 / 界面 /
Applied Energistics 2 / Sophisticated Backpacks / Curios API / Just Enough Items /
Sophisticated Core`，共 11 个（此前 12 个且名字中英混用）。

**4.1 标签栏固定一行、横向滚动**（按用户要求）

标签多了不再换行（换行会把列表挤扁），改成一行 + 鼠标滚轮横向滚动：

- `repositionElements` 把所有标签排在同一个 y 上，整体按 `tabScrollOffset` 左移；
- `mouseScrolled` 在鼠标位于标签栏高度时横向滚动（实测 `handled = true`）；
- 两端用 `<` `>` 提示是否还有内容，只在确实能滚的那一侧画；
- **部分露出的标签照常渲染**，不做「只显示完整标签」的过滤——用户明确要求：
  截断本身就是「后面还有」的提示，全藏起来就没法判断滚到哪儿了。

**4.2 部分按键不出现在设置界面里**（按用户要求，两轮调整）

第一版是「改键按钮置灰、点不动 + 悬停提示」，用户随后改为**整行都不显示**。

- `LayerKeyBindsList#rebuild` 在**分组之前**就用 `Arrays.stream(...).filter(...)` 滤掉锁定按键，
  所以不会留下空的分类/层级标题；
- `LayerKeyBindsScreen#hasMappingsFor` 同样跳过，免得出现「有标签但一行都没有」；
- 若某层级**只有**锁定按键（例如某些整合包把 `key.fullscreen` 单独拎出来），该标签会整体消失。

清单**硬编码**在 `core/LockedKeybinds.java`（用户明确说"直接写死"，不做成配置项）。
「重置按键」按钮仍统计所有按键（含锁定的），这样被配置文件改过的锁定键仍能恢复默认值。

实测（探针分别切到两个标签后截图）：「物品栏」标签下只剩 `E`（开启/关闭物品栏）与
创造模式的 `C`/`X`，快捷栏 `1`-`9` 整行消失；「系统」标签下只剩 `F2`（截图），
`F11`（切换全屏）整行消失。另有单元测试锁定这份清单。

**4.3 被锁定的键位不能被别的键占用**（用户指出）

锁定按键虽然不显示，却仍在 `options.keyMappings` 里参与冲突判定。玩家给「跳跃」绑 `1`
时会看到「冲突」红字，却找不到冲突对象（`key.hotbar.1` 被隐藏了）。

现在改键阶段就拦下来：`LayerKeyBindsScreen` 在 `keyReleased` / `mouseClicked` 里先查
`findLockedKeyUsing(candidate)`，命中就调 `rejectKeybind(owner)`——**不写入绑定、原绑定保持
不变**，并在屏幕底部显示 3 秒红色提示，**点名是谁占的**：

> 无法设置成该按键：已被「切换全屏显示」占用

（`findLockedKeyUsing` 返回的是占用的那个 `KeyMapping`，提示里取它的 `getDisplayName()`；
语言条目 `keybindlayers.keybind.reserved` 带一个 `%s` 占位符。）

实测：绑 `K`（空闲键）→ 空格变 `K`；绑 `1`（被 `key.hotbar.1` 占用）→ 仍是 `K`，未改动。

**4.4 改键期间的 F11 / F2 保护已修**

原版那个保护的判据写死了 `screen instanceof KeyBindsScreen`，对自建界面无效。这一处**返工了两次**，
坑都记在 DESIGN §4.6：

1. `INSTANCEOF` **不是合法注入点**（当前 Mixin 版本），直接崩在 `Initializing game`；
2. **只拦「切全屏」那个动作不够**——拦住 `toggleFullScreen` 后确实不切屏了，但分支末尾的
   `return` 照旧执行，**按键被吞掉、送不到界面**，界面从没记录到按下的 F11，最终把它绑成
   「未设置」（用户实测报回）。正解是拦那两个 `KeyMapping#matches`，
   让整段分支（含 return）被跳过；
3. `@At` 的 target 描述符**方法要用括号**，冒号只给字段——写成 `matches:(II)Z` 会报
   `InvalidMemberDescriptorException`。

另外判据也不能照抄原版：原版 `lastKeySelection` 只在选中按键时更新一次，那个 20ms 窗口
窄得没用。现在用 `LayerKeyBindsScreen#isAwaitingRebind` 为主、20ms 窗口为辅。

实测（完全走真实按键通路，并把 `lastKeySelection` 故意设成 10 秒前）：
绑空闲键 `K` → 成功；绑 `F11` → 原绑定不变、没变成「未设置」、没切全屏，
底部出现红字「无法设置成该按键：它已被锁定」。

**5. 按键行不再标层级**：曾经在每行按键名右侧画灰色层级小字，后按用户要求去掉，
层级信息只靠标签栏 + 两级标题表达。

**验证**（1.21.1，131 个按键）：层级汇总没有 `category:` 临时层
（system 2 / gameplay 55 / inventory 12 / social 4 / interface 58），
mod 专属作用域另计；列表共 158 行 = 131 个按键 + 5 个层级标题 + 22 个分类标题，
行序列为 `LAYER-HEADER → key… → LAYER-HEADER → CATEGORY-HEADER → key…`，与设计一致。

### 原版按键简化为两层（已完成，2026-10-10）

用户要求「简化一下原版的几个标签栏」，选定最激进的方案：**原版只留「游戏内操作」与「界面」**。

改的是 `KeyLayers.DEFAULT_CATEGORY_LAYERS`（它会写进新生成的配置，玩家可覆盖）：

| 原版分类 | 归到 |
|---|---|
| `movement`、`gameplay`、`misc` | `gameplay` |
| `ui`、`inventory`、`creative`、`multiplayer` | `interface` |

`DefaultAdaptations` 里原来那 6 条按键级映射删到只剩 3 条（`misc` 整体归游戏内之后，
`togglePerspective` / `smoothCamera` / `spectatorOutlines` 那三条成了冗余）：
`key.advancements` → `interface`，`key.drop` / `key.swapOffhand` → `gameplay`。

内置层级（`system` / `inventory` / `social` 等）**保留**，只是默认没有按键落上去，
所以不出现在标签栏；`LayerRules` 里 `SYSTEM` 仍作为 `category == null` 与 `STRICT` 策略的兜底。

**副作用（有意为之）**：原本不同层的按键现在同层，会开始互相报冲突——例如截图 `F2` 与跳跃
同键会标红（它们确实都在游戏内随时可按）。测试里那条「system 与 gameplay 不同层」的断言
因此改成了「gameplay 与 interface 不同层」。

实测标签栏：原版标签从 5 个（系统 / 游戏内操作 / 物品栏 / 社交 / 界面）减到 **2 个**
（游戏内操作 / 界面）；「界面」标签下 `E` / `T` / `Tab` / `/` / `P` 归类正确。

### 已完成的验证

1. **构建通过**（两个平台都跑过真实的 `gradlew build`，不是推测）：
   - `neoforge-1.21.1`：`BUILD SUCCESSFUL`（配置缓存 + 本地缓存命中，二次构建 4s）。
     产物 `build/libs/keybindlayers-neoforge-1.21.1-1.0.0.jar`，内含 mixin 类、
     `keybindlayers.mixins.json`、`META-INF/neoforge.mods.toml`、`pack.mcmeta`、语言文件。
   - `forge-1.20.1`：首次 `BUILD SUCCESSFUL`（6m19s，含下载 Gradle 8.8 / JDK 17 / Forge 依赖与反编译），
     二次 10s。产物 `build/libs/keybindlayers-forge-1.20.1-1.0.0.jar`。
   - 注解处理器输出已无警告（原先两条：`targets` 字符串 → 改用类字面量；
     `hasKeyModifierConflict` 找不到映射 → 加 `remap = false`）。
2. **refmap 已核实**（1.20.1 才需要）：jar 内 `keybindlayers.refmap.json` 中
   `same` → `m_90850_`、`refreshEntry()V` → `m_264257_()V`，目标类映射正确；
   `hasKeyModifierConflict` 按预期不在映射表中。原文见 [DESIGN.md](DESIGN.md) §2.1.2。
   注解处理器能生成这些条目，本身就是它成功解析到目标方法与调用点的证据。

### 待验证（下一步该做的第一件事）

**实机验证**：`.\gradlew.bat runClient`，打开「选项 → 控制」，确认：
   - 跨分类的同键**不再**有红色 `[ ]`、左侧红条与 tooltip；
   - 同一分类内的同键**仍然**标红；
   - 把 `config/keybindlayers.json` 的 `enabled` 改成 `false` 后行为完全回到原版。
3. **refmap 检查**（1.20.1）：构建后确认 `build/libs/*.jar` 内存在
   `keybindlayers.refmap.json`，且其中 `same` 被映射为 `m_90850_`。

**第二轮（1.21.1 界面）实机验证结果**（2026-10-10，用一次性探针自动截图 + 日志取证，探针已删除）：

已确证：

- **入口注入**：用 `mixin.debug.export` 导出变换后的 `ControlsScreen` 反汇编，
  `lambda$addOptions$1` 里 `KeyBindsScreen` 的实例先被
  `modify$zza000$keybindlayers$replaceKeyBindsScreen` 替换，再交给 `setScreen`；
  同一屏的 `lambda$addOptions$0`（鼠标设置）**原样未动**。`require = 0` 静默失效的隐患排除。
- **按键界面**：标题「按键绑定」、顶部层级标签栏（全部 / 系统 / 游戏内操作 / 物品栏 / 社交）、
  「全部」标签下的层级标题行与分类标题行、改键与重置按钮列、底部「重置按键 / 完成」，
  布局与原版一致。
- **冲突判定**（核心行为）：临时把 `key.togglePerspective` 与 `key.screenshot` 绑到同一个键
  （两者同属 `system`）→ 互相报冲突；再把 `key.jump`（`gameplay`）也绑到该键 →
  `conflicts = <none>`，**跨层级同键不报冲突**。界面如实渲染为红色 `[ F2 ]` + 左侧 3px 红条。
  探针随后自动恢复按键。
- **层级管理界面**：标题「按键层级设置」、「整合包作者模式：关」开关、层级列表
  （显示名 + id + 深度 + 内置标记）、编辑/删除按钮在只读模式下置灰、底部只读提示与
  「创建层级 / 完成」。
- **持久化端到端**：`LayerManager.create(...)` + `KeybindLayersClient.saveAndApply()` 之后，
  `keybindlayers.json` 出现 `packAuthorMode` 与 `layers` 字段；删除该层级后两者回到空，
  注册表同步移除（`KeyLayers.get(...) == null`）。

仍需人眼过一遍（探针覆盖不到）：

- 点标签切换内容、点「改键」进入等待输入状态、ESC 解绑、鼠标键与 `Shift+键` 组合的实际手感。
- 在层级管理界面里真正点击「创建层级 / 编辑 / 删除」的完整按钮流程（数据链路已验证，UI 点击未走）。
- 已知差异：等待改键时按 F11 会连带切全屏（原版 20ms 保护的判据是 `KeyBindsScreen` 类型，
  本界面不是它的子类）。

### 未完成 / TODO

- ~~两个工程都没有测试源集~~ —— **1.21.1 已加 JUnit 5**（`neoforge-1.21.1/src/test/java/`），
  `gradlew test` 即可跑，`build` 也会带上。13 个测试覆盖：层级增删改与 id 校验、删除后的
  引用回退、配置来源与 API 来源隔离、内置层级保护与深度不可覆盖、`applyDefinitions` 的
  注册与清理、默认适配表内容、判定开关与 `hasExplicitAssignment`。测试只碰 `shared/core`
  的纯逻辑，**不启动游戏**。1.20.1 工程还没加（那边没有平台特有的界面代码，加不加都行）。
- ~~游戏内配置界面没做~~ —— 1.21.1 已做（模组列表 → Config），1.20.1 仍只有 JSON。
- ~~层级名没有显示在「控制」界面里~~ —— 1.21.1 曾按需求在每行右侧用灰色小字标出层级，
  后按用户要求**去掉**：标签栏与分类标题已经表达了层级，行内再标一次是冗余。
- 1.20.1 侧尚未跟进第二轮的界面重做（用户确认本轮只做 1.21.1）。
- 默认适配目前只覆盖 JEI / JourneyMap / Xaero's Minimap + World Map / Mekanism。
  其余常见 mod 尚未适配，需要时照第三轮的做法实机确认分类字符串。已知但**未实机核实**的：
  AE2 疑似 `key.ae2.category`、Sophisticated Backpacks 用
  `key.category.sophisticatedbackpacks.main`（注意是单数 `category`）；Create 6.0 的语言
  文件里没有任何 `key.*`，可能已经不注册按键了。
- `DefaultAdaptations` 只作用于**新生成**的配置文件；已有 `keybindlayers.json` 不会自动补条目，
  想拿最新默认值需要删掉该文件重新生成。
- Fabric 未支持（用户未要求）。
- 1.21.2+ 未支持（`KeyMapping.Category` 是注册对象，不再是 `String`）。

## 3. 已知风险与坑

| 风险 | 说明 |
|---|---|
| `require = 0` 静默失效 | 注入点漂移时 mod 会安静地什么都不做。排查时先把 `require` 改成 1（两个 Mixin 注解 + `*.mixins.json` 的 `injectors.defaultRequire`）。 |
| 1.21.1 生产环境未做字节码核实 | 本机没有 NeoForge 1.21.1 的 SRG/joined 产物可比对。按 NeoForge 1.20.2+ 的惯例运行时为 Mojmap，故 `mixins.json` 里没写 `refmap`；若实机出现注入失败，优先怀疑这里。 |
| `@Redirect` 的 owner 写错就完全失效 | 必须写 `Lnet/minecraft/client/KeyMapping;hasKeyModifierConflict(...)Z`，**不要**写 `IForgeKeyMapping` / `IKeyMappingExtension`（详见 DESIGN.md §1.2）。 |
| 共享源码被两个 Java 版本编译 | `shared/` 同时被 Java 17（Forge）与 Java 21（NeoForge）编译，因此只能用 Java 17 语法；不要在里面用 `record` 模式匹配等更高版本特性（`record`/`switch` 表达式是 Java 17 允许的，但保持克制）。 |
| `gradle.properties` 的 ISO-8859-1 | `.properties` 按 ISO-8859-1 读取，`mod_description` 等字段保持 ASCII，中文放语言文件。 |
| 两个 Gradle 版本 | Forge 工程必须留在 Gradle 8.x（ForgeGradle 6 未验证 Gradle 9）；不要为了统一而升级。 |
| 配置只在启动时读一次 | 改完 `config/keybindlayers.json` 需要重启客户端。 |
| 导入「进行中」的会话会留下悬空 tool call | 导入一个仍在执行的会话（尤其**导入自己**）时，最后一步的 `tool/result` 还没写进源日志，新会话继续对话会直接报 `DeepSeek Messages tool calls need immediate results`。用 `tools/fix-imported-session.js` 修（见下节）。 |

## 3.1 修复导入会话：`tools/fix-imported-session.js`

工作区维护工具，与 mod 构建无关。DSH 的会话日志是「多帧 zstd、每帧多行事件」，
脚本会逐帧解压、给缺失的 tool call 补上 `tool/call` + `tool/result`（补录文本明确标注不是原始输出）、
重新连续编号 `seq`，然后按原来的帧划分写回。

```powershell
$f = "C:\Users\yuan2\.dsh\sessions\--E-mod-sources-Keybind~0020Layers--\import-session-<id>\session.v4.jsonl.zstd"
node "E:\mod-sources\Keybind Layers\tools\fix-imported-session.js" $f            # 只报告
node "E:\mod-sources\Keybind Layers\tools\fix-imported-session.js" $f --apply    # 备份后写入
```

两个注意事项：

1. **修复前先关掉那个会话**（最保险是退出 DSH）。GUI 内存里的会话状态会在之后追加事件时
   带着旧的 `seq` 计数器写回，可能造成 `seq` 冲突。
2. 修复后**重启 DSH 再打开该会话**，让它从文件重新加载 `seq`。

本工作区的导入会话 `import-session-52e334ca-…` 已经用此脚本修过一次（补了 3 条事件：
`import_chat` 与 `todo_write` 两个调用的结果），修复前备份为同目录下的
`session.v4.jsonl.zstd.bak-<时间戳>`。

## 4. 常用命令

```powershell
# 构建两个平台
cd "E:\mod-sources\Keybind Layers"; .\build-all.ps1

# 单独构建 / 运行
cd "E:\mod-sources\Keybind Layers\neoforge-1.21.1"; .\gradlew.bat build
cd "E:\mod-sources\Keybind Layers\neoforge-1.21.1"; .\gradlew.bat runClient
cd "E:\mod-sources\Keybind Layers\forge-1.20.1";    .\gradlew.bat build
```

## 5. 本轮已核实的本机事实（避免重复调研）

- 两个版本的 `KeyBindsList$KeyEntry#refreshEntry()V` 判据调用点：offset 102（`same`）与 114
  （`hasKeyModifierConflict`），owner 均为 `net/minecraft/client/KeyMapping`，全局唯一。
- 1.20.1 SRG：`same` → `m_90850_`，`hasKeyModifierConflict` 不重映射，
  `hasCollision` → `f_268447_`，`key` → `f_193910_`。
- 原版 7 个分类键名：`key.categories.{misc,ui,movement,gameplay,inventory,creative,multiplayer}`。
- 本机环境：默认 `java` 是 JDK 21.0.2，`C:\Program Files\Java` 下另有 jdk-8 / jdk-25 / jdk-26，
  **没有 JDK 17**；Gradle 发行版缓存只有 9.3.0 与 9.4.0。
- 参考工程：`E:\mod-sources\Meds and Herbs`（Forge 1.20.1 + MixinGradle 模板）、
  `E:\mod-sources\Towards the Stars`（NeoForge 1.21.1 + ModDevGradle 模板）。
