import org.gradle.api.tasks.bundling.Jar

plugins {
    kotlin("multiplatform") version "2.0.20"
    kotlin("plugin.serialization") version "2.0.20"
    // HTML output only, not `dokka-gfm` — Dokka's own README calls its
    // Markdown/GFM format "currently in Alpha," HTML is the stable default.
    // Html2Typst/Html2Mdx convert FROM this HTML; Dokka never emits Typst/MDX
    // itself.
    id("org.jetbrains.dokka") version "2.2.0"
}

repositories { mavenCentral() }

kotlin {
    // Pins the JDK used to compile AND run tests, matching CI's temurin-21 setup
    // regardless of whichever JDK happens to be on the host's PATH.
    jvmToolchain(21)

    // The commonMain core below is compiled to BOTH targets from one source set.

    // JVM -> the CLI used in CI: reads mystmd's _build/**.json, writes .mdx files.
    jvm()

    // JS -> the in-browser playground: paste a MyST AST, watch the MDX generate live.
    js(IR) {
        browser()
        binaries.executable()
        generateTypeScriptDefinitions()
        nodejs()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("com.github.ajalt.clikt:clikt:5.0.1")
                // HTML parser backing Html2Typst — jsoup, not a hand-rolled parser,
                // for the same reason mystmd (not our own MyST parser) backs the
                // MDX pipeline: don't reimplement a spec-compliant parser upstream
                // already solved.
                implementation("org.jsoup:jsoup:1.18.1")
            }
        }
        val jvmTest by getting
        val jsMain by getting
    }
}

// The CLI is invoked in CI as `java -jar tool/build/libs/*-jvm.jar`, so jvmJar
// must be a fat jar bundling the runtime classpath, not just the CLI's own classes.
tasks.named<Jar>("jvmJar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest { attributes["Main-Class"] = "blueprint.CliKt" }
    from(configurations["jvmRuntimeClasspath"].map { if (it.isDirectory) it else zipTree(it) })
}

// `gradle dokkaGenerate` -> build/dokka/html/ — the real HTML that Html2Typst/
// Html2Mdx are exercised against (see blueprint.dokka's tests and
// tool/dokka-html-fixture/ below). Not run by `make pipeline` or CI; this is
// the converter layer's own input, not part of the MyST->MDX site build.
dokka {
    dokkaPublications.named("html") {
        moduleName.set("myst2mdx")
        outputDirectory.set(layout.buildDirectory.dir("dokka/html"))
    }
}
