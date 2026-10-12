plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kover)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint.gradle)
}

android {
    namespace = "io.github.sgirishshiva.glyphwizard"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.sgirishshiva.glyphwizard"
        minSdk = 35
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.1"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    flavorDimensions += "sdk"
    productFlavors {
        create("sdkFree") {
            dimension = "sdk"
            isDefault = true
        }
        create("full") { dimension = "sdk" }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }
    buildFeatures { compose = true }
    lint {
        warningsAsErrors = true
        // target 36 is deliberate: target 37 forces the runtime ACCESS_LOCAL_NETWORK permission (D31)
        disable += "OldTargetApi"
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Robolectric reflects into jdk.internal.access, which JDK 25 no longer exports (robolectric.org getting started)
        unitTests.all { it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED") }
    }
}

detekt {
    buildUponDefaultConfig = true
    source.from("src/debug/kotlin", "src/release/kotlin", "src/testDebug/kotlin", "src/testRelease/kotlin")
}
ktlint { version = libs.versions.ktlint.get() }
kover { reports { variant("sdkFreeDebug") { verify { rule { minBound(80) } } } } }

dependencies {
    implementation(project(":core"))
    implementation(project(":designsystem"))
    implementation(project(":glyph-sdk"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlin.test)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)

    ktlintRuleset(libs.compose.rules.ktlint)
}
