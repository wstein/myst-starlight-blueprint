plugins {
    kotlin("multiplatform") version "2.0.20"
    kotlin("plugin.serialization") version "2.0.20"
}

repositories { mavenCentral() }

kotlin {
    // The commonMain core below is compiled to BOTH targets from one source set.

    // JVM -> the CLI used in CI: reads mystmd's _build/**.json, writes .mdx files.
    jvm {
        binaries { executable { mainClass.set("blueprint.CliKt") } }
    }

    // JS -> the in-browser playground: paste a MyST AST, watch the MDX generate live.
    js(IR) {
        browser()
        binaries.executable()
        generateTypeScriptDefinitions()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("com.github.ajalt.clikt:clikt:5.0.1")
            }
        }
        val jsMain by getting
    }
}
