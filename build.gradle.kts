plugins {
    id("java")  // Using the Java plugin
    id("org.openjfx.javafxplugin") version "0.0.13"  // Plugin for JavaFX
    id("application")  // Plugin for application support
}

group = "dev.IPOleksenko"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.json:json:20231013")
    implementation("com.google.code.gson:gson:2.8.9")  // Gson for working with JSON
    implementation("org.openjfx:javafx-controls:17")  // JavaFX for the UI
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

// Specify the main class that will be launched when using the application plugin
application {
    mainClass.set("dev.IPOleksenko.Main")
}
