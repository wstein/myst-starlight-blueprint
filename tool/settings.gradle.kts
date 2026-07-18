plugins {
    // Lets Gradle auto-provision the JDK pinned by `kotlin.jvmToolchain` below when
    // it isn't already installed, so the build is reproducible across machines.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "myst-mdx-transpiler"
