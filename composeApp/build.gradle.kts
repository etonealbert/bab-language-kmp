import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    // Add serialization plugin if you plan to use JSON (highly recommended)
    // kotlin("plugin.serialization") version "1.9.23"
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    // --- CHANGE 1: XCFramework Configuration for SPM ---
    // We define the XCFramework container here
    val xcf = XCFramework("Shared") // This will be the module name in Swift: import Shared

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared" // Must match the XCFramework name
            isStatic = false    // Dynamic is usually better for SPM local packages
            xcf.add(this)       // Link this target to the XCFramework task
        }
    }
    // ---------------------------------------------------

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            // Add Kable (Bluetooth) for Android specifically if needed
            implementation("com.juul.kable:core:0.30.0")
        }
        commonMain.dependencies {
            // --- EXISTING UI LIBS ---
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            // --- CHANGE 2: YOUR NEW ARCHITECTURE LIBS (MVI + Logic) ---
            // Note: I am using hardcoded versions here to ensure they work immediately.
            // You can move these to libs.versions.toml later.

            // 1. Decompose (Navigation & Lifecycle)
            implementation("com.arkivanov.decompose:decompose:3.1.0")
            implementation("com.arkivanov.decompose:extensions-compose:3.1.0")

            // 2. MVIKotlin (State Management)
            implementation("com.arkivanov.mvikotlin:mvikotlin:4.0.0")
            implementation("com.arkivanov.mvikotlin:mvikotlin-main:4.0.0")
            implementation("com.arkivanov.mvikotlin:mvikotlin-coroutines:4.0.0")

            // 3. Kable (Bluetooth Low Energy)
            implementation("com.juul.kable:core:0.30.0")

            // 4. Coroutines (Async logic)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

            // 5. Serialization (For saving Dialogs/Profiles to JSON)
            // implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "com.bablabs.bringabrainlanguage"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.bablabs.bringabrainlanguage"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}