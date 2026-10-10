plugins {
    id("net.neoforged.moddev")
    id("neoforge-mutex")
}

version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = property("mod.id") as String

repositories {
    mavenCentral()
}

neoForge {
    // 每个版本用自己那行的加载器版本，这就是「一个工程多版本」的关键。
    version = property("deps.neo_loader") as String

    mods {
        register("probe") {
            sourceSet(sourceSets.main.get())
        }
    }

    runs {
        register("client") {
            client()
        }
    }
}

java {
    val requiredJava = JavaVersion.VERSION_21
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava
    toolchain {
        vendor = JvmVendorSpec.ADOPTIUM
        languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
    }
}

tasks {
    processResources {
        val props = buildMap {
            put("id", sc.properties["mod.id"] ?: "probe")
            put("name", sc.properties["mod.name"] ?: "Probe")
            put("version", sc.properties["mod.version"] ?: "0.0.0")
            put("minecraft", sc.properties["mod.mc_compat"] ?: "*")
        }
        filesMatching("META-INF/neoforge.mods.toml") { expand(props) }
    }

    // Stonecutter 要先按当前版本改写源码，ModDevGradle 才能编译它。
    named("createMinecraftArtifacts") {
        dependsOn("stonecutterGenerate")
    }
}
