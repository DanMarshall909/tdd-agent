import org.gradle.api.tasks.JavaExec
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdea
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdeaCommunity
import org.jetbrains.intellij.platform.gradle.models.ProductRelease.Channel.RELEASE

plugins {
    kotlin("jvm") version "2.0.10"
    kotlin("plugin.serialization") version "2.0.10"
    id("org.jetbrains.kotlinx.kover") version "0.9.5"
    id("org.jetbrains.intellij.platform") version "2.11.0"
    id("info.solidsoft.pitest") version "1.9.11"
    id("com.diffplug.spotless") version "6.25.0"
    `maven-publish`
}

group = "dev.agent"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea("2025.1.7")
        bundledPlugin("org.jetbrains.kotlin")
    }

    // Kotlin
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // Testing
    testImplementation("io.kotest:kotest-runner-junit5:5.8.0")
    testImplementation("io.kotest:kotest-assertions-core:5.8.0")
    testImplementation("io.mockk:mockk:1.13.9")
    pitest("org.pitest:pitest-junit5-plugin:1.2.1")
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

intellijPlatform {
    buildSearchableOptions = false

    pluginConfiguration {
        version = "0.1.0"
        name = "TDD Agent"
        description = "IntelliJ plugin for TDD workflow"
        changeNotes = "Initial release"

        ideaVersion {
            sinceBuild = "251"
        }
    }

    pluginVerification {
        ides {
            select {
                types = listOf(IntellijIdeaCommunity)
                untilBuild = "252.*"
            }
            select {
                types = listOf(IntellijIdea)
                sinceBuild = "253"
                channels = listOf(RELEASE)
            }
        }
    }

    sandboxContainer.set(layout.buildDirectory.dir("idea-sandbox"))
}

tasks.named<org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask>("runIde") {
    jvmArguments.add("-Didea.kotlin.plugin.use.k2=false")
    args("C:\\Users\\Dan\\IdeaProjects\\untitled2")
}

pitest {
    pitestVersion.set("1.16.1")
    targetClasses.set(listOf("dev.agent.workflow.*"))
    targetTests.set(listOf("dev.agent.workflow.*"))
    junit5PluginVersion.set("1.2.1")
    threads.set(2)
    outputFormats.set(listOf("HTML", "XML"))
    timestampedReports.set(false)
}

tasks.register("mutationTest") {
    group = "verification"
    description = "Runs mutation testing using PIT"
    dependsOn("pitest")
}

tasks.register<JavaExec>("workflowCli") {
    group = "verification"
    description = "Run the workflow state machine CLI for manual exploration"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("dev.agent.workflow.WorkflowCliKt")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<Test> {
    useJUnitPlatform()
}

kover {
    reports {
        filters {
            excludes {
                classes("dev.agent.plugin.*")
            }
        }
    }
}
