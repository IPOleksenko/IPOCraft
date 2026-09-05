import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.bundling.Tar

plugins {
    id("java")
    id("application")
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "com.IPOleksenko"
version = "1.0.0"

repositories {
    mavenCentral()
}

val javafxVersion = "17.0.10"
val platforms = listOf("win", "linux", "mac")

dependencies {
    implementation("org.json:json:20231013")
    implementation("com.google.code.gson:gson:2.8.9")

    // Cross-platform JavaFX bundled directly inside the fat jar
    for (platform in platforms) {
        implementation("org.openjfx:javafx-base:$javafxVersion:$platform")
        implementation("org.openjfx:javafx-graphics:$javafxVersion:$platform")
        implementation("org.openjfx:javafx-controls:$javafxVersion:$platform")
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
}

application {
    mainClass.set("com.IPOleksenko.Launcher")
}

tasks.withType<ShadowJar> {
    archiveBaseName.set("IPOCraft")
    archiveClassifier.set("")
    archiveVersion.set("")
    archiveFileName.set("IPOCraft.jar")
    manifest {
        attributes(
            "Main-Class" to "com.IPOleksenko.Launcher"
        )
    }
    mergeServiceFiles()
}

// Distribution packaging - standalone executable JAR
task("buildWin", type = Zip::class) {
    dependsOn("shadowJar")
    from(tasks.named<ShadowJar>("shadowJar").map { it.archiveFile })
    archiveFileName.set("IPOCraft_win.zip")
    destinationDirectory.set(file("$buildDir/distributions"))
}

task("buildLinux", type = Tar::class) {
    dependsOn("shadowJar")
    from(tasks.named<ShadowJar>("shadowJar").map { it.archiveFile })
    archiveFileName.set("IPOCraft_linux.tar.gz")
    destinationDirectory.set(file("$buildDir/distributions"))
    compression = Compression.GZIP
}

task("buildMac", type = Tar::class) {
    dependsOn("shadowJar")
    from(tasks.named<ShadowJar>("shadowJar").map { it.archiveFile })
    archiveFileName.set("IPOCraft_mac.tar.gz")
    destinationDirectory.set(file("$buildDir/distributions"))
    compression = Compression.GZIP
}

tasks.build {
    dependsOn("shadowJar")
}

