# Keybind Layers

> 替换原版的按键绑定冲突检测逻辑：**只有属于同一操作层级的按键，才会在「控制」设置界面里被显示为冲突。**

同一个键在不同操作层级里承担不同功能（例如 `E` 在游戏内是打开物品栏、在某个界面里是「提取全部」、
在载具里是「下船」）本来互不干扰——它们永远不会在同一时刻响应。但原版把
`Options.keyMappings` 里所有按键摊平成一个列表两两比较，于是这些**假冲突**会被标成红色。
本 mod 给按键引入「操作层级」概念，并把设置界面的冲突判定改成层级感知的。

## 支持矩阵

| 目标 | 加载器 | 状态 | 工程目录 |
|---|---|---|---|
| Minecraft 1.20.1 | Forge 47.x | 已实现 | [`forge-1.20.1/`](forge-1.20.1) |
| Minecraft 1.21.1 | NeoForge 21.1.x | 已实现 | [`neoforge-1.21.1/`](neoforge-1.21.1) |

两个工程共用 [`shared/`](shared) 下的同一份核心源码，只有 Mixin 的目标类名不同
（1.21.1 把 `KeyBindsList` 挪进了 `screens.options.controls` 包）。

## 默认行为

开箱即用，无需配置：

| 原版分类 | 归入层级 |
|---|---|
| `key.categories.movement` / `gameplay` / `misc` | `gameplay`（游戏内操作） |
| `key.categories.ui` / `inventory` / `creative` / `multiplayer` | `interface`（界面） |
| 其它（mod 自己的分类） | 每个分类各自成层（策略 `own_layer`） |

原版按键**只分两层**：能在游戏内直接生效的（移动、攻击、跳跃、截图）归「游戏内操作」；
要打开或操作界面的（背包、聊天、命令、进度、社交屏幕）归「界面」——免得原版把标签栏占满。
mod 按键则优先采用它们自己声明的作用域，见下文。

也就是说：**跨层级的同键不再报冲突，同一层级内部的同键照旧报冲突。**

## 安装

1. 装好对应加载器：**Forge 47.x**（1.20.1）或 **NeoForge 21.1.x**（1.21.1）；
2. 把 jar 放进 `.minecraft/mods/`；
3. 进游戏后「选项 → 控制」就是新的层级化界面。

本 mod **只在客户端生效**，专用服务器不需要装、装了也不会有副作用。

## 构建

两个工程是独立的 Gradle 构建（ForgeGradle 6 与 ModDevGradle 2 对 Gradle 版本的要求不同），
因此分别构建：

```powershell
# NeoForge 1.21.1（Gradle 9.3.0，JDK 21）
cd neoforge-1.21.1
.\gradlew.bat build

# Forge 1.20.1（Gradle 8.8，需要 JDK 17 —— 由 foojay 解析器自动下载）
cd forge-1.20.1
.\gradlew.bat build
```

或一次构建两个：`.\build-all.ps1`。

产物分别在 `forge-1.20.1/build/libs/` 与 `neoforge-1.21.1/build/libs/`。

开发环境运行客户端（会打开带本 mod 的游戏）：

```powershell
.\gradlew.bat runClient
```

## 测试

`neoforge-1.21.1` 里有一组 JUnit 测试，只覆盖 `shared/core` 的纯逻辑，**不启动游戏**：

```powershell
cd neoforge-1.21.1
.\gradlew.bat test
```

覆盖层级增删改与 id 校验、删除后的引用回退、配置来源与 API 来源隔离、内置层级保护与
深度不可覆盖、默认适配表内容、以及判定开关。`gradlew build` 也会带上测试。

## 配置

首次启动会在 `config/keybindlayers.json` 生成一份**带注释**的配置——mod 写出的文件里每个
字段都有中文说明，**你手写的注释也会被保留并忽略**（读取端用 Gson 的宽容模式，跳过 `//`
与 `/* */`）。下面为了紧凑省略了注释：

```json
{
  "enabled": true,
  "unknownCategoryPolicy": "own_layer",
  "packAuthorMode": false,
  "separateModifierCombinations": true,
  "useDeclaredKeyContext": true,
  "layers": {},
  "categories": {
    "key.categories.misc": "gameplay",
    "key.categories.ui": "interface",
    "key.categories.movement": "gameplay",
    "key.categories.gameplay": "gameplay",
    "key.categories.inventory": "interface",
    "key.categories.creative": "interface",
    "key.categories.multiplayer": "interface",
    "jm.common.hotkeys_keybinding_category": "gameplay",
    "jm.common.hotkeys_keybinding_fullscreen_category": "interface",
    "Xaero's Minimap": "gameplay",
    "Xaero's World Map": "interface"
  },
  "mappings": {
    "key.categories.misc|key.advancements": "interface",
    "key.categories.inventory|key.drop": "gameplay",
    "key.categories.inventory|key.swapOffhand": "gameplay",
    "Xaero's Minimap|gui.xaero_minimap_settings": "interface",
    "Xaero's Minimap|gui.xaero_minimap_server_profiles": "interface",
    "Xaero's Minimap|gui.xaero_waypoints_key": "interface"
  },
  "logLayerDecisions": false
}
```

- `enabled`：`false` 时完全退回原版的全按键冲突检测。
- `unknownCategoryPolicy`：未识别分类的归层策略
  - `own_layer`（默认）：每个未知分类各自成层，分类之间永不互报冲突
  - `shared_layer`：所有未知分类共用一个 `mods` 层，跨 mod 同键仍会报冲突（更保守）
  - `strict`：所有分类视为同层，等同原版行为
- `packAuthorMode`：「整合包作者」模式。开启后可以在层级管理界面里创建 / 修改 / 删除层级；
  关闭时该界面只读，避免普通玩家误改整合包作者分好的层级。
- `separateModifierCombinations`：是否把组合键与单键分开看待，默认 `true`。
  开启时 `W` 与 `Shift+W` 不再互相标红——按 `W` 并不会触发 `Shift+W`，玩家把两者绑到
  不同功能通常是有意的组合；只有**修饰键相同**的两个绑定才会报冲突。
  设为 `false` 退回原版行为。这个开关只影响冲突显示，不影响按键实际触发。
- `useDeclaredKeyContext`：是否采用按键**自己声明**的生效作用域，默认 `true`。
  NeoForge 允许 mod 在注册按键时声明它是只在游戏内生效（`IN_GAME`）还是只在界面里生效
  （`GUI`），这正好就是本 mod 说的「操作层级」，而且是 mod 作者自己写的。
  开启后：配置里显式写过的按键仍以配置为准，没写过的才看这个声明；声明是 `UNIVERSAL`
  （NeoForge 默认值）或自定义 context 时继续走分类映射与未知分类策略。
  1.20.1 侧没有接入这个读取器（Forge 的 context 在另一个包），此项在那边无效果。
- `layers`：自定义层级定义，键是层级 id，值是 `{ displayName, order }`。
  - 内置的 8 个层级不写在这里也始终存在；写进去只会改它们的显示名，深度仍以内置值为准。
  - `order` 越小越「底层」，只影响标签栏与列表排序，不参与冲突判定。
  - `displayName` 留空时回退到语言文件 `keybindlayers.layer.<id>`，再找不到就直接显示 id。
- `categories`：分类 → 层级 id。
- `mappings`：`分类|按键名` → 层级 id，优先级高于分类级，用来把分类里的个别按键单独挪层。
- 删除一个层级时，引用它的分类 / 按键会自动回退到 `unknownCategoryPolicy` 的兜底规则。
- 配置文件在启动时读取；层级管理界面里的改动能立即写盘并生效，手工改文件需要重启客户端。
- **上面这些适配只写进新生成的配置文件**。已经有 `keybindlayers.json` 的话不会被自动改写，
  想拿到最新默认值可以删掉该文件让游戏重新生成。

### 默认分层是怎么定的

三条来源，优先级从高到低：

1. **配置里的显式条目**（`categories` / `mappings`）——玩家写什么就是什么，下面的内置适配表也在这一层；
2. **按键自己声明的作用域**（`useDeclaredKeyContext`）——NeoForge 的 `IN_GAME` / `GUI`，
   只有配置里没写过的按键才会走到这一步；
3. **未知分类策略**（`unknownCategoryPolicy`）——兜底，默认每个分类各自成层。

#### 原版按键的按用途细分（`mappings`）

原版按键没有作用域声明（全是 `UNIVERSAL`），只能按分类手工归层。按需求「简化标签栏」，
**原版只分两层**：

| 原版分类 | 归到 | 理由 |
|---|---|---|
| `movement` / `gameplay` / `misc` | **游戏内操作** | 移动、攻击、跳跃，以及截图这类随时可按的键，都会与其它游戏内操作同时响应 |
| `ui` / `inventory` / `creative` / `multiplayer` | **界面** | 打开背包、聊天、命令、进度、社交屏幕，都是打开或操作界面时才生效 |

只有几个键与所属分类不一致，用单键覆盖修正：`key.advancements`（进度，打开 GUI）归「界面」，
`key.drop` / `key.swapOffhand` 归「游戏内操作」。

内置层级本身仍保留 `system` / `inventory` / `social` 等——配置里随时可以把按键指回去，
只是默认没有按键落在上面，所以不出现在标签栏。

#### mod 这边是分情况处理的

实测（装 JEI / JourneyMap / Mekanism / Xaero's，共 97 个 mod 按键），按键声明作用域的情况
分三类，处理方式也不同：

| mod | 处理方式 | 原因 |
|---|---|---|
| **Mekanism** | 自动判定 | 10 个键全部声明了标准作用域（`IN_GAME` / `GUI`），而且比手工更准：`key.mekanism.module_tweaker` 手工版曾误判成 `interface`，实际声明是 `IN_GAME` |
| **JEI** | 自动判定，**各自成层** | 用的是自定义 context（`JeiForgeKeyConflictContexts`），判断的是「当前屏幕是不是 JEI 界面」。这类按**作用域类型**分开成层：JEI 界面的键一层，与别的 mod 界面互不冲突 |
| JourneyMap | 适配表：小地图组 → `gameplay`，全屏组 → `interface` | 同组里混着 `UNIVERSAL` 按键，只靠自动判定会把同一组拆到两个层级 |
| Xaero's Minimap | 适配表：整组 → `gameplay`，3 个打开界面的键 → `interface` | 30 个键全是 `UNIVERSAL`，没有任何声明 |
| Xaero's World Map | 适配表：整组 → `interface` | 同上 |

**为什么自定义 context 要各自成层**：不同 mod 的界面永远不会同时打开，把它们统统压进
`interface` 层，会让「JEI 的悬停查配方」和「Mekanism 的模块详情」绑同一个键时标红——那是
**假冲突**，恰恰是本 mod 该消灭的。而同一个界面里的键确实会同时生效，所以保持同层。

没适配到、也没有声明的 mod 一律落到 `unknownCategoryPolicy`（默认每个分类各自成层），
所以**不会**因为没适配就报出跨 mod 的假冲突。

### 列表是怎么排的

「控制」界面的按键列表按**层级 → 分类**两级组织：

- **层级标题**（居中、白色）：只在「全部」标签下显示；
- **分类标题**（左对齐、灰色）：层级内再按 `KeyMapping#getCategory()` 分段，也就是按 mod
  （原版按键则按原版分类）。**层内只有一个分类时不显示**，免得与层级标题重复。

### 标签栏

**标签按 mod 归并**：`category:xxx`（兜底层）与 `context:xxx`（mod 自定义作用域）这类层级，
标签名取**所属 mod 的显示名**——从层级 id 里按 `.` 与 `$` 切段去问 `ModList`，例如
`category:key.ae2.category` → 「Applied Energistics 2」、
`context:mezz.jei.neoforge.input.JeiForgeKeyConflictContexts` → 「Just Enough Items」。

于是**同一个 mod 的多个层级会并进同一个标签**（Sophisticated Backpacks 有 4 个层级，
现在共用一个标签），但它们仍然是**各自独立的层级**——判定上互不冲突，只是显示在一起。

取不到 mod 名的层级（含原版分类）继续用分类的本地化名，也就是「系统」「游戏内操作」这些。

标签栏**固定占一行**：放不下时把鼠标放在标签栏上滚滚轮横向滚动，两端用 `<` `>` 提示是否
还有内容；被截断的标签照常渲染一半，这样能一眼看出滚到了哪儿。

**有些自定义作用域会被并进「游戏内操作」**：不少 mod 的按键条件只是「装备/手持了某样东西」
——例如 Sophisticated Backpacks 的「打开背包」——它们在游戏进行中随时可用，会和移动、攻击
同时响应，本该属于游戏内层，而不是单开一个界面标签。判定办法是在**没有界面打开**的时候调
一次它们的 `isActive()`：返回 `true` 的就是这类（`BackpackKeyConflictContext` 正如此，
它只看有没有界面，不看背包有没有装备）；JEI、容器界面这类此时返回 `false`，自然保持独立。

### 锁定的按键

有几类键位**根本不出现在设置界面里**（整行都不显示，不是置灰）：

- **快捷栏 `1`-`9`**：与数字键的对应关系被大量 mod 与玩家的肌肉记忆依赖，改掉没好处；
- **全屏 `F11`**：跨游戏的通用快捷键。

实现上是在列表分组**之前**就把它们滤掉，所以也不会留下空的分类/层级标题；生成标签时
同样跳过，免得出现「有标签但一行都没有」。这份清单是**硬编码**的
（`shared/java/.../core/LockedKeybinds.java`），要调整就改那个类。

**别的键也不能绑到它们占用的键位**：这些键虽然不显示，却仍在 `keyMappings` 里参与冲突判定。
如果不拦，玩家给「跳跃」绑 `1` 时会看到一条「冲突」红字却找不到冲突对象（它被隐藏了）。
所以改键阶段直接拒绝，并**点名是谁占的**：「无法设置成该按键：已被「切换全屏显示」占用」，
原绑定保持不变。

「重置按键」按钮仍统计所有按键（含锁定的），万一这些键被配置文件之类改过，点它还能恢复默认值。

## 给其它 mod 的 API

包 `caibai_fish.keybindlayers.api.KeybindLayersApi`（纯 Java，无 Minecraft 类型，可安全软依赖）：

```java
// 注册自己的层级（order 越小越底层，仅影响排序）
KeybindLayersApi.registerLayer("aircraft", 130);

// 把整个分类归入某层级
KeybindLayersApi.assignCategory("key.categories.myvehicles", "vehicle");

// 只把分类里的某个按键挪层（优先级高于分类级）
KeybindLayersApi.assignMapping("key.categories.myvehicles", "key.myvehicles.landing_gear", "aircraft");
```

未安装本 mod 时用 `ModList.get().isLoaded("keybindlayers")` 判断后再调用即可。

## 目录结构

```
Keybind Layers/
├── shared/                     两个工程共用的源码与语言文件
│   ├── java/caibai_fish/keybindlayers/
│   │   ├── core/               纯逻辑：层级模型、判定规则、配置读写（不依赖 MC）
│   │   ├── api/                对外 API
│   │   └── client/             KeyMapping 桥接与客户端初始化（依赖 MC，两版 API 一致）
│   └── resources/assets/keybindlayers/lang/
├── forge-1.20.1/               ForgeGradle 6 工程（Gradle 8.8 / JDK 17）
├── neoforge-1.21.1/            ModDevGradle 2 工程（Gradle 9.3 / JDK 21）
└── docs/
    ├── DESIGN.md               设计说明与字节码依据
    └── HANDOFF.md              交接清单：已完成 / 待验证 / 已知风险
```

## 许可

MIT
