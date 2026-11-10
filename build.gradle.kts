import java.io.File

plugins {
    java
}

fun serverJar(): File {
    val configured = (findProperty("hytaleServerJar") as String?)
        ?: System.getenv("HYTALE_SERVER_JAR")
    if (!configured.isNullOrBlank()) {
        return file(configured)
    }
    val sibling = rootDir.resolve("../../data/HytaleServer.jar")
    return if (sibling.isFile) sibling else rootDir.resolve("libs/HytaleServer.jar")
}

group = "com.jaredscarito"
version = "1.3.7"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    compileOnly(files(serverJar()))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.jar {
    archiveFileName.set("HyFactions.jar")
}
