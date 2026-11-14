// One Minecraft version of the Forge build with Essential's fork of Architectury Loom; the core (../core) compiles into each jar.
import java.util.Properties

plugins {
    java
    id("gg.essential.loom")
    id("dev.architectury.architectury-pack200")
    id("com.github.johnrengelman.shadow")
}

val baseGroup: String by project
val mcVersion: String by project
val mappingsVersion: String by project
val forgeVersion: String by project
val modid: String by project
// The mod version and the description come from the modern build; -Pmod_version sets a different version, as in the modern build.
val modernProperties = Properties().apply { rootProject.file("../modern/gradle.properties").inputStream().use { load(it) } }
val modVersion = providers.gradleProperty("mod_version").orNull ?: modernProperties.getProperty("mod_version")

version = "$modVersion+$mcVersion"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))
}

sourceSets.main {
    java.srcDir(rootProject.file("src/main/java"))
    java.srcDir(rootProject.file("../core/src/main/java"))
    resources.srcDir(rootProject.file("src/main/resources"))
    // The core resources (the sky shaders) go into the jar next to the core classes.
    resources.srcDir(rootProject.file("../core/src/main/resources"))
    output.setResourcesDir(sourceSets.main.flatMap { it.java.classesDirectory })
}

loom {
    launchConfigs {
        "client" {
            property("mixin.debug", "true")
            arg("--tweakClass", "org.spongepowered.asm.launch.MixinTweaker")
        }
    }
    runConfigs {
        remove(getByName("server"))
    }
    forge {
        pack200Provider.set(dev.architectury.pack200.java.Pack200Adapter())
        mixinConfig("mixins.$modid.json")
    }
    mixin {
        defaultRefmapName.set("mixins.$modid.refmap.json")
    }
}

repositories {
    mavenCentral()
    maven("https://repo.spongepowered.org/maven/")
}

val shadowImpl: Configuration by configurations.creating {
    configurations.implementation.get().extendsFrom(this)
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    mappings(mappingsVersion)
    forge(forgeVersion)

    // The runtime and the processor cannot be one version.
    // The runtime is 0.7.11 because it has its own ASM (org.spongepowered.asm.lib, which LegacySkyPatch, MixinPlugin and
    // OptiFineRenderPatch use): 0.8 needs ASM 5.2 (ClassRemapper), and Forge 1.8.9 has ASM 5.0.3.
    // The processor is 0.8.5 because the 0.7.11 processor fails on the obfuscation service that Loom adds (AbstractMethodError).
    val mixinRuntime = "org.spongepowered:mixin:0.7.11-SNAPSHOT"
    val mixinProcessor = "org.spongepowered:mixin:0.8.5-SNAPSHOT"
    shadowImpl(mixinRuntime) {
        isTransitive = false
    }
    annotationProcessor(mixinProcessor)
}

tasks.withType(JavaCompile::class) {
    options.encoding = "UTF-8"
}

tasks.withType(org.gradle.jvm.tasks.Jar::class) {
    archiveBaseName.set("$modid-forge")
    manifest.attributes.run {
        this["FMLCorePluginContainsFMLMod"] = "true"
        this["ForceLoadAsMod"] = "true"
        this["TweakClass"] = "org.spongepowered.asm.launch.MixinTweaker"
        this["MixinConfigs"] = "mixins.$modid.json"
    }
}

// The client mixins are the classes of the mixin package in the shared source and in the source of this version, in ASCII order.
val mixins = listOf(rootProject.file("src/main/java/dev/albedo/legacy/mixin"), file("src/main/java/dev/albedo/legacy/mixin"))
    .flatMap { dir -> dir.listFiles { f -> f.name.endsWith(".java") }?.toList() ?: emptyList() }
    .map { it.name.removeSuffix(".java") }
    .sorted()
    .joinToString(",\n    ") { "\"$it\"" }

tasks.processResources {
    inputs.property("mixins", mixins)
    inputs.property("version", project.version)
    inputs.property("mcversion", mcVersion)
    inputs.property("modid", modid)
    inputs.property("baseGroup", baseGroup)
    inputs.property("mod_description", modernProperties.getProperty("mod_description"))
    filesMatching(listOf("mcmod.info", "mixins.$modid.json")) {
        expand(inputs.properties)
    }
}

val remapJar by tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar") {
    archiveClassifier.set("")
    from(tasks.shadowJar)
    input.set(tasks.shadowJar.get().archiveFile)
}

tasks.jar {
    archiveClassifier.set("without-deps")
    destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
}

tasks.shadowJar {
    destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
    archiveClassifier.set("non-obfuscated-with-deps")
    configurations = listOf(shadowImpl)
}

tasks.assemble.get().dependsOn(tasks.remapJar)

// Loom 0.10's own runClient fails Gradle 8's task validation, so this runs the same launch.cfg as a plain JavaExec on Java 8.
tasks.register<JavaExec>("runClientDev") {
    group = "minecraft"
    description = "Starts the $mcVersion Forge dev client."
    // Only downloadAssets extracts the LWJGL natives of a Minecraft version that the shared Loom cache does not have.
    dependsOn("classes", "downloadAssets")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("net.fabricmc.devlaunchinjector.Main")
    // -PrunDir=<folder> gives the harness a game folder for each suite, so the suites of a node run at the same time.
    workingDir = file(project.findProperty("runDir") ?: "run")
    doFirst { workingDir.mkdirs() }
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(8)) })
    jvmArgs(
        "-Dfabric.dli.config=${file(".gradle/loom-cache/launch.cfg").absolutePath}",
        "-Dfabric.dli.env=client",
        "-Dfabric.dli.main=net.minecraft.launchwrapper.Launch",
    )
    // -Pharness=<dir> enables the file-driven test harness (see core Harness) at the size -Pharness_window=<w>x<h>.
    if (project.hasProperty("harness")) {
        systemProperty("albedo.harness", File(project.property("harness") as String).absolutePath)
        val (width, height) = (project.findProperty("harness_window") ?: "1600x900").toString().split("x")
        args("--width", width, "--height", height)
    }
}
