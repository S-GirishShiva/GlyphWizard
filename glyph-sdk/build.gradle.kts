plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kover)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint.gradle)
}

android {
    namespace = "io.github.sgirishshiva.glyphwizard.glyph"
    compileSdk {
        version = release(37)
    }
    defaultConfig { minSdk = 35 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }
    flavorDimensions += "sdk"
    productFlavors {
        create("sdkFree") {
            dimension = "sdk"
            isDefault = true
        }
        create("full") { dimension = "sdk" }
    }
    lint { warningsAsErrors = true }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Robolectric reflects into jdk.internal.access, which JDK 25 no longer exports (robolectric.org getting started)
        unitTests.all { it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED") }
    }
}

detekt {
    buildUponDefaultConfig = true
    source.from("src/sdkFree/kotlin", "src/full/kotlin", "src/testSdkFree/kotlin", "src/testFull/kotlin")
}
ktlint { version = libs.versions.ktlint.get() }
kover {
    reports {
        filters { excludes { classes("*.SdkMatrixService") } } // thin SDK adapter, needs a real device
        variant("sdkFreeDebug") { verify { rule { minBound(80) } } }
        variant("fullDebug") { verify { rule { minBound(80) } } }
    }
}

dependencies {
    api(project(":core"))
    "fullImplementation"(libs.glyph.matrix.sdk) { artifact { type = "aar" } }

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.coroutines.test)
}
