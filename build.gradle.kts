plugins {
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.serialization") version "1.9.24"
    id("org.jetbrains.intellij") version "1.16.1"
    id("com.diffplug.spotless") version "6.23.3"
    `maven-publish`
}

group = "dev.agent"
version = "0.1.0"

repositories {
    mavenCentral()
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint("0.50.0")
            .setEditorConfigPath(file(".editorconfig"))
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint("0.50.0")
            .setEditorConfigPath(file(".editorconfig"))
    }
}

dependencies {
    // Kotlin
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // Testing
    testImplementation("io.kotest:kotest-runner-junit5:5.8.0")
    testImplementation("io.kotest:kotest-assertions-core:5.8.0")
    testImplementation("io.mockk:mockk:1.13.9")
}

intellij {
    version.set("2024.1")
}

kotlin {
    jvmToolchain(21)
}

java {
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<Test> {
    useJUnitPlatform()
}
