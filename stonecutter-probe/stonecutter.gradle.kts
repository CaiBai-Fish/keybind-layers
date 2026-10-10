plugins {
    id("dev.kikugie.stonecutter")
    id("net.neoforged.moddev") version "2.0.147" apply false
}

stonecutter active "1.21.1"

// 集中式属性见 stonecutter.properties.toml，这里只做必要的替换。
stonecutter parameters {
    constants["release"] = false
}
