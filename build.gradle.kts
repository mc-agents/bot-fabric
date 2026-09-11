plugins {
    id("net.fabricmc.fabric-loom")
}

val javaVersion = JavaVersion.toVersion(property("java_version") as String)

version = "${property("mod_version")}+${stonecutter.current.version}"
group = property("mod_group") as String
base.archivesName = property("mod_id") as String

dependencies {
    minecraft("com.mojang:minecraft:${stonecutter.current.version}")
    implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")

    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

/* The pieces that are ours and not Minecraft's are testable without a client. */
tasks.test { useJUnitPlatform() }

loom {
    runConfigs.all {
        runDirectory = rootProject.layout.projectDirectory.dir("run")
        generateRunConfig = true
        preferGradleTask = true
    }
    runConfigs.named("client") {
        val botName = providers.gradleProperty("bot.name").orElse("fabric_bot")
        programArguments.addAll(providers.provider { listOf("--username", botName.get()) })
        /* BotConfig reads an environment name lowercased with dots, so these track those names. */
        systemProperties.put("mcp.server.host", providers.gradleProperty("rpc.host").orElse("127.0.0.1"))
        systemProperties.put("mcp.server.port", providers.gradleProperty("rpc.port").orElse("8765"))
        systemProperties.put("bot.rpc.enabled", providers.gradleProperty("rpc.enabled").orElse("true"))
        systemProperties.put("bot.name", botName)
    }
}

java {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion.majorVersion)
    }
}

tasks {
    withType<JavaCompile>().configureEach {
        options.release = javaVersion.majorVersion.toInt()
        options.encoding = "UTF-8"
    }

    processResources {
        val props = mapOf(
            "id" to project.property("mod_id"),
            "name" to project.property("mod_name"),
            "version" to project.version,
            "minecraft" to project.property("mc_compat"),
            "loader" to project.property("loader_version"),
            "java" to javaVersion.majorVersion
        )
        props.forEach { (k, v) -> inputs.property(k, v) }
        filesMatching(listOf("fabric.mod.json", "*.mixins.json")) { expand(props) }
    }
}
