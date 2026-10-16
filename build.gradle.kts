plugins {
    id("net.fabricmc.fabric-loom")
}

val modId = sc.properties.get<String>("mod.id")
val modName = sc.properties.get<String>("mod.name")
val modVersion = sc.properties.get<String>("mod.version")
val mcVersion = sc.current.version

// Nombre del jar: <modid>-<versión del mod>+mc<versión>.jar
version = "$modVersion+mc$mcVersion"
group = sc.properties.get<String>("mod.group")
base.archivesName = modId

repositories {
    // Cada grupo se busca solo en su repositorio
    fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
        forRepository { maven(url) { name = alias } }
        filter { groups.forEach(::includeGroup) }
    }
    strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")
}

loom {
    splitEnvironmentSourceSets()

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets.getByName("client"))
        }
    }

    runConfigs.all {
        runDirectory = rootProject.file("run")
        generateRunConfig = true
    }
}

fabricApi {
    configureTests {
        createSourceSet = true          // src/gametest/java + src/gametest/resources
        modId = "$modId-test"
        enableGameTests = true          // runGameTest (servidor dedicado)
        enableClientGameTests = true    // runClientGameTest
        eula = true                     // imprescindible: sin esto el servidor de la CI no arranca
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    implementation("net.fabricmc:fabric-loader:${sc.properties.get<String>("deps.fabric_loader")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${sc.properties.get<String>("deps.fabric_api")}")

    // Integraciones opcionales: compileOnly aquí + FabricLoader.isModLoaded(...) en ejecución
    compileOnly("maven.modrinth:modmenu:${sc.properties.get<String>("compat.modmenu")}")
    compileOnly("maven.modrinth:placeholder-api:${sc.properties.get<String>("compat.placeholder_api")}")

    if (project.hasProperty("sodium") || project.hasProperty("iris")) {
        localRuntime("maven.modrinth:sodium:${sc.properties.get<String>("compat.sodium")}")
    }
    if (project.hasProperty("iris")) {
        localRuntime("maven.modrinth:iris:${sc.properties.get<String>("compat.iris")}")
    }

    // Modpack de compatibilidad (va en la CI): ./gradlew :26.3:runGameTest -PcompatPack
    if (project.hasProperty("compatPack")) {
        localRuntime("maven.modrinth:lithium:${sc.properties.get<String>("compat.lithium")}")
        localRuntime("maven.modrinth:ferrite-core:${sc.properties.get<String>("compat.ferritecore")}")
    }

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
}

tasks {
    withType<JavaCompile>().configureEach {
        options.release = 25
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
    }

    withType<JavaExec>().configureEach {
        if (project.hasProperty("meteorOnly")) systemProperty("liveevents.meteorOnly", "true")
    }

    test {
        useJUnitPlatform()
        // GeneratedDocsTest: escribe (o comprueba) el JSON Schema y la referencia de módulos
        systemProperty("liveevents.root", rootProject.projectDir.absolutePath)
        systemProperty("liveevents.generateDocs", project.hasProperty("generateDocs").toString())
        inputs.property("generateDocs", project.hasProperty("generateDocs"))
    }

    processResources {
        val props = mapOf(
            "id" to modId,
            "name" to modName,
            "version" to version.toString(),
            "minecraft" to sc.properties.get<String>("mod.mc_compat"),
            "loader" to sc.properties.get<String>("deps.fabric_loader"),
        )
        inputs.properties(props)
        filesMatching("fabric.mod.json") { expand(props) }
    }

    // El JSON Schema publicado también va dentro del jar (para /le validate y editores offline)
    named<ProcessResources>("processResources") {
        from(rootProject.file("schema")) { into("assets/liveevents/schema") }
    }

    jar {
        from(rootProject.file("CREDITS.md"))
        from(rootProject.file("LICENSE")) { rename { "${it}_$modId" } }
        manifest.attributes(
            "Implementation-Title" to modName,
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "TakumiStudios",
        )
    }
}
