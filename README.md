# Keybind Layers

> 替换原版的按键绑定冲突检测逻辑：**仅当两个按键属于同一操作层级时，才会在「控制」界面中被标记为冲突。**

同一个按键在不同操作层级中承担不同功能（例如 `E` 在游戏内打开物品栏、在某个界面中执行「提取全部」、
在载具中用于下船），这些用途不会在同一时刻响应。原版将 `Options.keyMappings` 中的全部按键展开为
单一列表两两比较，因而产生**假冲突**。本 mod 为按键引入「操作层级」概念，并将设置界面的
冲突判定改为层级感知。

## 版本支持

| 目标 | 加载器 | 状态 | 下载 |
|---|---|---|---|
| Minecraft 1.21.1 | NeoForge 21.1.x | 已实现 | [keybindlayers-neoforge-1.21.1-1.0.0.jar](https://github.com/CaiBai-Fish/keybind-layers/releases/download/v1.0.0/keybindlayers-neoforge-1.21.1-1.0.0.jar) |
| Minecraft 1.21.1 | Fabric Loader 0.19.x + Fabric API | 已实现 | [keybindlayers-fabric-1.21.1-1.0.0.jar](https://github.com/CaiBai-Fish/keybind-layers/releases/download/v1.0.0/keybindlayers-fabric-1.21.1-1.0.0.jar) |

全部发行版见 [Releases](https://github.com/CaiBai-Fish/keybind-layers/releases)。源码工程目录：
[`neoforge-1.21.1/`](neoforge-1.21.1)、[`fabric-1.21.1/`](fabric-1.21.1)。

1.21.1 上两个加载器功能对等：「选项 → 控制」替换为带层级标签栏的界面，并提供从
「模组列表 → 配置」进入的层级管理界面（含「整合包作者」开关）。

> **1.20.1 已暂时移出。** 该版本的冲突判定本身可用，但界面重做需要另行实现布局：
> 1.20.1 同样提供 `OptionsSubScreen`，但位于 `screens` 包下，且不具备 1.21.1 所用的
> `addContents()` / `addFooter()` 钩子与 `layout` 字段。当前仅维护 1.21.1 的两个加载器。

## 安装

1. 安装对应加载器：**NeoForge 21.1.x**，或 **Fabric Loader 0.19.x + Fabric API**（1.21.1）；
2. 将 jar 放入 `.minecraft/mods/`；
3. 启动游戏，「选项 → 控制」即为层级化界面。

Fabric 版**必须同时安装 Fabric API**：mod 的语言文件依赖其 `fabric-resource-loader-v0`
加载，缺失时界面文本会退化为 `keybindlayers.tab.all` 一类的原始翻译键。
若同时安装 ModMenu，「模组列表 → 配置」可打开层级管理界面（可选）。

本 mod **仅在客户端生效**。专用服务器无需安装，安装后也不会产生副作用。

## 配置文件

首次启动会在 `config/keybindlayers.json` 生成一份**带注释**的配置：mod 写出的文件中每个
字段都附有中文说明，**手写注释同样会被保留并由读取端忽略**（读取时使用 Gson 的宽容模式，
跳过 `//` 与 `/* */`）。下列示例为便于阅读省略了注释：

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

- `enabled`：设为 `false` 时完全退回原版的全按键冲突检测。
- `unknownCategoryPolicy`：未识别分类的归层策略。
  - `own_layer`（默认）：每个未知分类各自成层，分类之间永不互报冲突；
  - `shared_layer`：所有未知分类共用 `mods` 层，跨 mod 同键仍会报冲突（更保守）；
  - `strict`：所有分类视为同层，等同原版行为。
- `packAuthorMode`：「整合包作者」模式。启用后可在层级管理界面中创建、修改、删除层级；
  关闭时该界面为只读，以避免普通玩家误改整合包作者划分的层级。
- `separateModifierCombinations`：是否将组合键与单键分开处理，默认 `true`。
  启用时 `W` 与 `Shift+W` 不再互相标记冲突——按下 `W` 不会触发 `Shift+W`，将两者绑定到
  不同功能通常是有意为之；仅当**修饰键相同**时才判定为冲突。设为 `false` 退回原版行为。
  该开关只影响冲突显示，不影响按键的实际触发。Fabric 的原版 `KeyMapping` 没有修饰键概念，
  此项在 Fabric 上不生效。
- `useDeclaredKeyContext`：是否采用按键**自身声明**的生效作用域，默认 `true`。
  NeoForge 允许 mod 在注册按键时声明其仅在游戏内生效（`IN_GAME`）或仅在界面中生效（`GUI`），
  这正是本 mod 所指的「操作层级」，且由 mod 作者本人给出。启用后：配置中显式写过的按键以
  配置为准，未写过的才参考该声明；声明为 `UNIVERSAL`（NeoForge 默认值）或自定义 context 时，
  继续走分类映射与未知分类策略。**Fabric 没有这个概念**（原版 `KeyMapping` 不存在作用域
  声明），此项在 Fabric 上不生效。
- `layers`：自定义层级定义，键为层级 id，值为 `{ displayName, order }`。
  - 8 个内置层级即使不写入也始终存在；写入只会修改其显示名，深度仍以内置值为准。
  - `order` 越小越「底层」，仅影响标签栏与列表排序，不参与冲突判定。
  - `displayName` 留空时回退到语言文件 `keybindlayers.layer.<id>`，仍找不到则直接显示 id。
- `categories`：分类 → 层级 id 的映射。
- `mappings`：`分类|按键名` → 层级 id，优先级高于分类级，用于将分类中的个别按键单独挪层。
- 删除某个层级时，引用它的分类与按键会自动回退到 `unknownCategoryPolicy` 的兜底规则。
- 配置文件在启动时读取；层级管理界面中的改动能立即写盘并生效，手工修改文件则需重启客户端。
- **上述适配只会写入新生成的配置文件**。已有的 `keybindlayers.json` 不会被自动改写；
  如需获取最新默认值，可删除该文件令游戏重新生成。

## 其它文档

其余内容由 wiki 维护：

- [工作原理](https://github.com/CaiBai-Fish/keybind-layers/wiki/How-it-works) ——
  层级来源、默认两层的划分依据、需要手工适配的 mod、自定义作用域为何各自成层
- [控制界面说明](https://github.com/CaiBai-Fish/keybind-layers/wiki/Controls-screen) ——
  标签栏、列表排序规则、被锁定的按键
- [构建与测试](https://github.com/CaiBai-Fish/keybind-layers/wiki/Building) ——
  两个工程的工具链差异、目录结构、JUnit 测试
- [给其它 mod 的 API](https://github.com/CaiBai-Fish/keybind-layers/wiki/API) ——
  注册层级、按分类或按单个按键归层

## 许可

MIT
