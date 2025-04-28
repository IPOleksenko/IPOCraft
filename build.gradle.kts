import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.bundling.Tar

plugins {
    id("java")
    id("org.openjfx.javafxplugin") version "0.0.13"
    id("application")
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "dev.IPOleksenko"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.json:json:20231013")
    implementation("com.google.code.gson:gson:2.8.9")
    implementation("org.openjfx:javafx-controls:17")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

javafx {
    version = "17"
    modules("javafx.controls")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("dev.IPOleksenko.Main")
}

tasks.withType<ShadowJar> {
    archiveBaseName.set("IPOCraft")  // Set base name for the JAR file
    archiveExtension.set("jar")  // Set file extension
    archiveFileName.set("IPOCraft.jar")  // Set full file name
    manifest {
        attributes(
            "Main-Class" to "dev.IPOleksenko.Main"
        )
    }
    mergeServiceFiles()
}

// Copy native JavaFX libraries
val copyJavafxNatives by tasks.registering(Copy::class) {
    val osName = org.gradle.internal.os.OperatingSystem.current()
    val platform = when {
        osName.isWindows -> "win"
        osName.isMacOsX -> "mac"
        osName.isLinux -> "linux"
        else -> throw GradleException("Unknown operating system: ${osName.name}")
    }

    from(configurations.runtimeClasspath.get().filter { it.name.contains("javafx") && it.name.contains(platform) })
    into("$buildDir/libs/natives")
}

// Scripts for different operating systems
task("generateRunScript") {
    doLast {
        val osName = org.gradle.internal.os.OperatingSystem.current()
        val scriptContent = when {
            osName.isWindows -> """
                @echo off
                java --module-path libs;natives --add-modules javafx.controls -jar IPOCraft.jar
            """.trimIndent()
            osName.isMacOsX -> """
                #!/bin/bash
                java --module-path libs:natives --add-modules javafx.controls -jar IPOCraft.jar
            """.trimIndent()
            osName.isLinux -> """
                #!/bin/bash
                java --module-path libs:natives --add-modules javafx.controls -jar IPOCraft.jar
            """.trimIndent()
            else -> throw GradleException("Unsupported OS: ${osName.name}")
        }

        val scriptFile = File("$buildDir/libs/run.sh")
        scriptFile.writeText(scriptContent)

        // For Windows, create a .bat file
        if (osName.isWindows) {
            val batFile = File("$buildDir/libs/run.bat")
            batFile.writeText(scriptContent.replace("java --module-path libs;natives", "java --module-path libs;natives"))
        }
    }
}

// Tasks to create archives for each OS

// Windows - .zip
task("buildWin", type = Zip::class) {
    dependsOn("shadowJar", "copyJavafxNatives", "generateRunScript")
    from("$buildDir/libs") {
        include("IPOCraft.jar", "natives/**", "run.bat")
        into("IPOCraft")
    }
    archiveFileName.set("IPOCraft_win.zip")
    destinationDirectory.set(file("$buildDir/distributions"))
}

// Linux - .tar.gz
task("buildLinux", type = Tar::class) {
    dependsOn("shadowJar", "copyJavafxNatives", "generateRunScript")
    from("$buildDir/libs") {
        include("IPOCraft.jar", "natives/**", "run.sh")
        into("IPOCraft")
    }
    archiveFileName.set("IPOCraft_linux.tar.gz")
    destinationDirectory.set(file("$buildDir/distributions"))
    compression = Compression.GZIP
}

// Mac - .tar.gz
task("buildMac", type = Tar::class) {
    dependsOn("shadowJar", "copyJavafxNatives", "generateRunScript")
    from("$buildDir/libs") {
        include("IPOCraft.jar", "natives/**", "run.sh")
        into("IPOCraft")
    }
    archiveFileName.set("IPOCraft_mac.tar.gz")
    destinationDirectory.set(file("$buildDir/distributions"))
    compression = Compression.GZIP
}

// Configure the build task so that it does not trigger all archives at once
tasks.build {
    dependsOn("shadowJar")
}
