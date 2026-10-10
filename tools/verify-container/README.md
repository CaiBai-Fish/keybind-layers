# 跨版本实机验证容器（模板）

拿一份**已经编译好的** mod jar，在**任意 NeoForge 版本**上实机跑一遍，回答
「这个 jar 到底能不能装在别的 Minecraft 版本上」。

## 用法

1. 把本目录的两个文件复制进一个临时工程：

   ```
   <tmp>/build.gradle                              <- 本目录的
   <tmp>/settings.gradle                           <- 见下方骨架
   <tmp>/gradle.properties                         <- org.gradle.configuration-cache=false 即可
   <tmp>/gradlew, gradlew.bat, gradle/wrapper/     <- 从任一现有工程复制
   <tmp>/src/main/java/verify/VerifyProbe.java     <- 本目录的
   <tmp>/src/main/resources/META-INF/neoforge.mods.toml
   ```

   `settings.gradle` 骨架（与 `neoforge-1.21.1` 相同，只改 `rootProject.name`）：

   ```groovy
   pluginManagement {
       repositories {
           mavenLocal()
           gradlePluginPortal()
           maven { url = 'https://maven.neoforged.net/releases' }
       }
   }
   plugins { id 'org.gradle.toolchains.foojay-resolver-convention' version '0.8.0' }
   rootProject.name = 'verify-<mc>'
   ```

2. 把 `build.gradle` 里的 `neoForge { version = '21.x.y' }` 改成**目标运行环境**的
   NeoForge 版本（不是待测 jar 的编译版本）。
3. 把待测 jar 放进 `run/mods/`。
4. `gradlew runClient`。

探针会在主菜单出现后自动走「选项 → 控制 → 按键绑定」，把每一步结果打进日志、
截一张图，然后自己退出。

## 判据

- `PROBE keybindlayers loaded = true` —— jar 被加载了。
- `PROBE interception = OK` —— 目标 mod 的界面接管生效（`ControlsScreenMixin` 起作用）。
- 崩在 `Rendering screen` / `NoSuchMethodError` —— jar 加载得起来但 API 对不上，
  崩溃报告的完整栈会指明是哪个方法。

## 已知坑

- `prepareClientRun` 会因为 TLS 抖动报
  `Could not download neoforge-<ver>-moddev-config.json`，**多试几次就好**
  （这个文件只在 `runClient` 时才需要，`build` 不会下它）。
- 手动把文件塞进 Gradle 缓存没用，离线模式仍报 `No cached version available`。
- `GuiEventListener` 没有 `children()`，要找按钮得判 `ContainerEventHandler`。
- 探针必须登记在工程自己的 `mods { }` 源集里，否则不会被加载。
- 别同时跑两个 `runClient`，后启动的会抢 `latest.log`。
