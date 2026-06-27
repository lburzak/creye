import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask
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
    // Gephi publishes a few legacy transitive artifacts here, including stax-utils.
    maven("https://raw.github.com/gephi/gephi/mvn-thirdparty-repo/")
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
    // ADR-013: ForceAtlas2 comes from Gephi's JVM layout implementation. These Gephi
    // NetBeans modules use nbm packaging, so the adapter requests their jar artifacts
    // explicitly; Gephi 0.10.1 pins graphstore to 0.6.14 in its parent POM.
    implementation("org.gephi:layout-plugin:0.10.1@jar")
    implementation("org.gephi:layout-api:0.10.1@jar")
    implementation("org.gephi:graphstore:0.6.14")
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
    pluginVerification {
        failureLevel.set(listOf(VerifyPluginTask.FailureLevel.NOT_DYNAMIC))
        ides {
            create(IntelliJPlatformType.IntellijIdeaCommunity, "2025.2")
        }
    }
}
