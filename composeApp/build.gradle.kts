import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.skie)

    id("maven-publish")
}

kotlin {
    // 1. Android Target (Library Mode)
    androidTarget {
        publishLibraryVariants("release") // Publish only the release version
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    // 2. iOS Target (XCFramework)
    // We export dependencies so Swift can see "Store", "StateFlow", etc.
    val xcf = XCFramework("BabLanguageSDK")

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "BabLanguageSDK"
            isStatic = true // Static frameworks are often easier for SPM distribution

            // CRITICAL: Export the architecture libs so they are visible in Swift
            export(libs.decompose)
            export(libs.mvikotlin.main)
            // Do NOT export Coroutines/Serialization usually (implementation details)

            xcf.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            // --- ARCHITECTURE (The "Brain") ---

            // 1. Decompose (Navigation)
            // Use core only. Remove "extensions-compose" to keep SDK pure.
            api(libs.decompose)

            // 2. MVIKotlin (State)
            api(libs.mvikotlin)
            api(libs.mvikotlin.main)
            implementation(libs.mvikotlin.extensions.coroutines)

            // 3. Networking & Data
            implementation(libs.ktor.client.core) // You'll need this
            implementation(libs.ktor.client.content.negotiation)

            // 4. Bluetooth
            implementation("com.juul.kable:core:0.30.0")

            // 5. Coroutines (Async)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

            // 6. Serialization
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

            // 7. DateTime (Cross-platform timestamps)
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")

            // --- REMOVED UI LIBS ---
            // compose.runtime, foundation, material3, etc. are GONE.
            // androidMain dependencies for compose are GONE.
        }

        androidMain.dependencies {
            // Android-specific logic (e.g. Ktor OkHttp engine)
            implementation(libs.ktor.client.okhttp)
        }

        iosMain.dependencies {
            // iOS-specific logic (e.g. Ktor Darwin engine)
            implementation(libs.ktor.client.darwin)
        }

        commonTest.dependencies {
            implementation("org.jetbrains.kotlin:kotlin-test:2.0.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
        }
    }
}

android {
    namespace = "com.bablabs.bringabrain.sdk" // Differentiate SDK namespace
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    // Publishing configuration for Android
    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

skie {
    features {
        enableSwiftUIObservingPreview = true
    }
}

// 3. Maven Publishing (Android)
publishing {
    publications {
        create<MavenPublication>("maven") {
            afterEvaluate {
                from(components["release"])
            }
            groupId = "com.bablabs"
            artifactId = "brain-sdk"
            version = "1.0.0" // Change this for every release
        }
    }
    repositories {
        // Publish to GitHub Packages
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/YOUR_GITHUB_USER/bab-language-kmp")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}