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

group = "com.IPOleksenko"
version = "1.0.0"

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

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
}

application {
    mainClass.set("com.IPOleksenko.Main")
}

tasks.withType<ShadowJar> {
    archiveBaseName.set("IPOCraft")  // Set base name for the JAR file
    archiveExtension.set("jar")  // Set file extension
    archiveFileName.set("IPOCraft.jar")  // Set full file name
    manifest {
        attributes(
            "Main-Class" to "com.IPOleksenko.Main"
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
                java -Dfile.encoding=UTF-8 --module-path libs;natives --add-modules javafx.controls -jar IPOCraft.jar
            """.trimIndent()
            osName.isMacOsX -> """
                #!/bin/bash
                java -Dfile.encoding=UTF-8 --module-path libs:natives --add-modules javafx.controls -jar IPOCraft.jar
            """.trimIndent()
            osName.isLinux -> """
                #!/bin/bash
                java -Dfile.encoding=UTF-8 --module-path libs:natives --add-modules javafx.controls -jar IPOCraft.jar
            """.trimIndent()
            else -> throw GradleException("Unsupported OS: ${osName.name}")
        }

        val scriptFile = File("$buildDir/libs/run.sh")
        scriptFile.writeText(scriptContent)

        // For Windows, create silent run.bat and IPOCraft.vbs
        if (osName.isWindows) {
            val winScript = """
                @echo off
                setlocal enabledelayedexpansion
                set "JAVA_CMD=javaw"
                if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javaw.exe" set "JAVA_CMD=%JAVA_HOME%\bin\javaw.exe"
                if exist "C:\Program Files\Microsoft\jdk-11.0.16.101-hotspot\bin\javaw.exe" set "JAVA_CMD=C:\Program Files\Microsoft\jdk-11.0.16.101-hotspot\bin\javaw.exe"
                start "" "%JAVA_CMD%" -Dfile.encoding=UTF-8 --module-path libs;natives --add-modules javafx.controls -jar IPOCraft.jar
                exit
            """.trimIndent()
            val batFile = File("$buildDir/libs/run.bat")
            batFile.writeText(winScript)

            val vbsContent = """
                Set WshShell = CreateObject("WScript.Shell")
                WshShell.Run chr(34) & "run.bat" & chr(34), 0, False
            """.trimIndent()
            File("$buildDir/libs/IPOCraft.vbs").writeText(vbsContent)
            File("$buildDir/libs/run.vbs").writeText(vbsContent)
        }
    }
}

// Tasks to create archives for each OS

// Windows - .zip
task("buildWin", type = Zip::class) {
    dependsOn("shadowJar", "copyJavafxNatives", "generateRunScript")
    from("$buildDir/libs") {
        include("IPOCraft.jar", "natives/**", "run.bat", "run.vbs", "IPOCraft.vbs")
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
