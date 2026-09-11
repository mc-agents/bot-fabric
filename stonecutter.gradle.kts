plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.1.2"

tasks.register("printVersions") {
    group = "help"
    val versions = stonecutter.versions.map { it.project to it.version }
    doLast {
        println(versions.joinToString(",", "[", "]") {
            """{"project":"${it.first}","minecraft":"${it.second}"}"""
        })
    }
}

tasks.register("buildAll") {
    group = "build"
    dependsOn(stonecutter.tasks.named("build").map { it.values })
}

stonecutter parameters {
    swaps["mod_version"] = "\"${property("mod_version")}\";"
    swaps["minecraft"] = "\"${node.metadata.version}\";"
}
