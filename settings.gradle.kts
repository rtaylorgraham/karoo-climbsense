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
        // karoo-ext resolves via JitPack (no auth needed).
        // If this ever fails: switch to the official GitHub Packages repo below (needs a
        // PAT with read:packages in ~/.gradle/gradle.properties as gpr.user / gpr.key)
        // and change the dependency in gradle/libs.versions.toml to
        // group = "io.hammerhead", name = "karoo-ext".
        maven(url = "https://jitpack.io")
        // maven {
        //     url = uri("https://maven.pkg.github.com/hammerheadnav/karoo-ext")
        //     credentials {
        //         username = providers.gradleProperty("gpr.user").getOrElse(System.getenv("USERNAME") ?: "")
        //         password = providers.gradleProperty("gpr.key").getOrElse(System.getenv("TOKEN") ?: "")
        //     }
        // }
    }
}

rootProject.name = "climbsense"
include(":app")
