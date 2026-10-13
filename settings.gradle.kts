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
        // The Nothing Glyph Matrix SDK (full flavour only; never committed, D18/D26) is a file in Nothing's GitHub repo.
        // Resolving it through Gradle tries every address the CDN resolves to, unlike the JDK's HTTP clients, and puts
        // it under dependency verification. The revision is a commit SHA, so the content can't change underneath us.
        exclusiveContent {
            forRepository {
                ivy {
                    name = "NothingGlyphSdk"
                    url = uri(providers.gradleProperty("glyphSdk.repository").get())
                    patternLayout { artifact("Nothing-Developer-Programme/GlyphMatrix-Developer-Kit/[revision]/[module].[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeModule("com.nothing", "glyph-matrix-sdk-2.0") }
        }
    }
}

rootProject.name = "GlyphWizard"
include(":core", ":agent", ":evals", ":designsystem", ":glyph-sdk", ":app", ":spike")
// deliberate detekt violations (D47 canary); only built when asked for
if (providers.gradleProperty("withCanary").isPresent) include(":detekt-canary")
