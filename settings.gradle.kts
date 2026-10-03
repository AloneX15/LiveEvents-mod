pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
    }
    plugins {
        id("net.fabricmc.fabric-loom") version "1.18.2"
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        // Una build por versión de Minecraft soportada (26.x en adelante)
        versions("26.1.2", "26.2", "26.3")
        vcsVersion = "26.3"
    }
}

rootProject.name = "liveevents"
