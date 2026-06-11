import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21"
    id("org.jetbrains.intellij.platform") version "2.7.0"
}

group = "pl.lukaszburzak.creye"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // ADR-001: baseline platform target IntelliJ IDEA 2025.2 (branch 252).
        intellijIdeaCommunity("2025.2")
        // ADR-001/ADR-006: Kotlin Analysis API comes from the platform-bundled Kotlin plugin.
        bundledPlugin("org.jetbrains.kotlin")
        // ADR-003: git comparison executes through the bundled git integration.
        bundledPlugin("Git4Idea")
        // ADR-009: Compose/Skiko/Jewel are provided by the target platform.
        // Keep this list aligned with META-INF/plugin.xml runtime module dependencies.
        bundledModules(
            "intellij.libraries.compose.foundation.desktop",
            "intellij.libraries.skiko",
            "intellij.platform.jewel.foundation",
            "intellij.platform.jewel.ui",
            "intellij.platform.jewel.ideLafBridge",
        )
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        apiVersion = KotlinVersion.KOTLIN_2_1
        languageVersion = KotlinVersion.KOTLIN_2_1
    }
}

intellijPlatform {
    buildSearchableOptions = false
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "252"
            untilBuild = provider { null }
        }
    }
}
