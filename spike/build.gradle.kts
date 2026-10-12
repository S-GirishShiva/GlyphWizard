plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kover)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint.gradle)
}

android {
    namespace = "io.github.sgirishshiva.glyphwizard.spike"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.sgirishshiva.glyphwizard.spike"
        minSdk = 35
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.1-spike"
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
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Robolectric reflects into jdk.internal.access, which JDK 25 no longer exports (robolectric.org getting started)
        unitTests.all { it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED") }
    }
    lint {
        warningsAsErrors = true
        // target 36 is deliberate (D31); the throwaway spike is exempt from UI and text rules (D53)
        disable += listOf("OldTargetApi", "MissingApplicationIcon", "HardcodedText")
    }
}

detekt { buildUponDefaultConfig = true }
ktlint { version = libs.versions.ktlint.get() }

dependencies {
    implementation(project(":core"))
    implementation(project(":glyph-sdk"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.coroutines.test)

    ktlintRuleset(libs.compose.rules.ktlint)
}
