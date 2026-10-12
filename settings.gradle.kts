pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "GlyphWizard"
include(":core", ":agent", ":evals", ":designsystem", ":glyph-sdk", ":app", ":spike")
// deliberate detekt violations (D47 canary); only built when asked for
if (providers.gradleProperty("withCanary").isPresent) include(":detekt-canary")
