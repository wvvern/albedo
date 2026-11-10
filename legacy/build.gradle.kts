// Loads the plugins one time so that the version subprojects share one Loom class loader.
plugins {
    id("gg.essential.loom") version "0.10.0.+" apply false
    id("dev.architectury.architectury-pack200") version "0.1.3" apply false
    id("com.github.johnrengelman.shadow") version "8.1.1" apply false
}
