pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        // 1.21.x 全系列里有稳定 NeoForge 的那些（1.21.2/6/7/9 当时只有 beta）。
        versions(
            "1.21",
            "1.21.1",
            "1.21.3",
            "1.21.4",
            "1.21.5",
            "1.21.8",
            "1.21.10",
            "1.21.11",
        )
        vcsVersion = "1.21.1"
    }
}

rootProject.name = "Stonecutter Probe"
