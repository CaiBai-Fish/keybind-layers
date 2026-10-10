# Keybind Layers

> 替换原版的按键绑定冲突检测逻辑：**只有属于同一操作层级的按键，才会在「控制」设置界面里被显示为冲突。**

同一个键在不同操作层级里承担不同功能（例如 `E` 在游戏内是打开物品栏、在某个界面里是「提取全部」、
在载具里是「下船」）本来互不干扰——它们永远不会在同一时刻响应。但原版把
`Options.keyMappings` 里所有按键摊平成一个列表两两比较，于是这些**假冲突**会被标成红色。
本 mod 给按键引入「操作层级」概念，并把设置界面的冲突判定改成层级感知的。

## 版本支持

| 目标 | 加载器 | 状态 | 工程目录 |
|---|---|---|---|
| Minecraft 1.21.1 | NeoForge 21.1.x | 已实现 | [`neoforge-1.21.1/`](neoforge-1.21.1) |
| Minecraft 1.21.1 | Fabric Loader 0.19.x + Fabric API | 已实现 | [`fabric-1.21.1/`](fabric-1.21.1) |

1.21.1 上两个加载器**功能对等**：「选项 → 控制」被换成层级标签栏的界面，另有从
「模组列表 → 配置」进入的层级管理界面（含「整合包作者」开关）。

> **1.20.1 已暂时移出。** 那边的冲突判定本来是好的，但界面重做得另写一套布局——1.20.1 也有
> `OptionsSubScreen`，只是它在 `screens` 包下，而且没有 1.21.1 那套 `addContents()` /
> `addFooter()` 钩子与 `layout` 字段。先只维护 1.21.1 的两个加载器。

## 安装

1. 装好对应加载器：**NeoForge 21.1.x** 或 **Fabric Loader 0.19.x + Fabric API**（1.21.1）；
2. 把 jar 放进 `.minecraft/mods/`；
3. 进游戏后「选项 → 控制」就是新的层级化界面。

Fabric 版**必须同时装 Fabric API**——mod 的语言文件要靠它的 `fabric-resource-loader-v0`
才会被加载，少了它界面上的文字会变成 `keybindlayers.tab.all` 这种原始翻译键。
另外装了 ModMenu 的话，「模组列表 → 配置」可以打开层级管理界面（这是可选的）。

本 mod **只在客户端生效**，专用服务器不需要装、装了也不会有副作用。

## 配置文件

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
  Fabric 上原版 `KeyMapping` 没有修饰键概念，此项在那边无效果。
- `useDeclaredKeyContext`：是否采用按键**自己声明**的生效作用域，默认 `true`。
  NeoForge 允许 mod 在注册按键时声明它是只在游戏内生效（`IN_GAME`）还是只在界面里生效
  （`GUI`），这正好就是本 mod 说的「操作层级」，而且是 mod 作者自己写的。
  开启后：配置里显式写过的按键仍以配置为准，没写过的才看这个声明；声明是 `UNIVERSAL`
  （NeoForge 默认值）或自定义 context 时继续走分类映射与未知分类策略。
  **Fabric 上没有这个概念**（原版 `KeyMapping` 不存在作用域声明），此项在那边无效果。
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

## 其它文档

其余内容都放进了 wiki：

- [工作原理](https://github.com/CaiBai-Fish/keybind-layers/wiki/How-it-works) ——
  层级从哪来、默认的两层是怎么定的、哪些 mod 需要手工适配、自定义作用域为何各自成层
- [控制界面说明](https://github.com/CaiBai-Fish/keybind-layers/wiki/Controls-screen) ——
  标签栏、列表排序规则、被锁定的按键
- [构建与测试](https://github.com/CaiBai-Fish/keybind-layers/wiki/Building) ——
  两个工程的工具链差异、目录结构、JUnit 测试
- [给其它 mod 的 API](https://github.com/CaiBai-Fish/keybind-layers/wiki/API) ——
  注册层级、按分类或按键归层

## 许可

MIT
