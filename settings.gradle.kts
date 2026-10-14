import org.gradle.kotlin.dsl.maven
import org.gradle.kotlin.dsl.repositories

include(":app", ":contract")


rootProject.name = "MiAuto"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        // Fallback for networks where jitpack.io is unreachable (Gradle reports
        // "Remote host terminated the handshake"). com.github.topjohnwu.libsu:core is published on
        // JitPack only, so without this the build cannot resolve its dependencies at all. Tencent's
        // aggregate repository proxies maven central / google / jitpack. It is consulted only after
        // google() and mavenCentral() miss, and it is harmless on networks that can reach JitPack;
        // drop this block if you would rather not use a third-party mirror.
        maven {
            url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        }

        maven {
            url = uri("https://jitpack.io")
        }
    }
}
