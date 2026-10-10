import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

// 防止 NeoForge 在多版本下并行重编译 Minecraft 把机器拖垮：
// 把 createMinecraftArtifacts 限制成同一时刻只跑一个。
interface NeoForgeMutex : BuildService<BuildServiceParameters.None>

val mutex = gradle.sharedServices.registerIfAbsent("createMinecraftArtifactsMutex", NeoForgeMutex::class.java) {
    maxParallelUsages.set(1)
}

tasks.named { it == "createMinecraftArtifacts" }.configureEach {
    usesService(mutex)
}
