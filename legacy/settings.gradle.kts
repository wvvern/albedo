pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.architectury.dev/")
        maven("https://maven.fabricmc.net")
        maven("https://maven.minecraftforge.net/")
        maven("https://repo.spongepowered.org/maven/")
        maven("https://repo.essential.gg/repository/maven-releases/")
    }
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "gg.essential.loom" -> useModule("gg.essential:architectury-loom:${requested.version}")
            }
        }
    }
}

plugins {
    // Set for the Gradle 8.8 wrapper of this build; modern/ runs Gradle 9 with 1.0.0.
    id("org.gradle.toolchains.foojay-resolver-convention") version ("0.8.0")
}

rootProject.name = "albedo-legacy"

// Each Minecraft version is a subproject in versions/<mc> that the shared version.gradle.kts builds.
// tools/project.py reads this listOf; keep it the first listOf of the file and on one line.
for (mc in listOf("1.8.9", "1.12.2")) {
    include(mc)
    project(":$mc").apply {
        projectDir = file("versions/$mc")
        buildFileName = "../../version.gradle.kts"
    }
}
