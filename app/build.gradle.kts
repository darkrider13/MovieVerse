plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Signing values are provided by the GitHub workflow (from GitHub Secrets). Same key on every build = update installs over the old app.
val ksPass: String? = System.getenv("KS_PASS")

android {
    namespace = "com.trackerxx.movieverse"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.trackerxx.movieverse"
        minSdk = 23
        targetSdk = 35
        // always rising (minutes since 1970) so a new APK installs over the previous one
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = "1.0.0"
        // "owner/repo" of the GitHub project, filled in automatically by GitHub Actions: Settings > Update looks for the new APK there
        buildConfigField("String", "UPDATE_REPO", "\"" + (System.getenv("GITHUB_REPOSITORY") ?: "") + "\"")
    }

    signingConfigs {
        if (ksPass != null) {
            create("mv") {
                storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
                storePassword = ksPass
                keyAlias = System.getenv("KS_ALIAS")
                keyPassword = System.getenv("KEY_PASS")
            }
        }
    }

    buildTypes {
        debug {
            if (ksPass != null) {
                signingConfig = signingConfigs.getByName("mv")
            }
        }
        // the APK that is published is the RELEASE build: not debuggable, shrunk + optimised by R8 -> Compose scrolls much smoother
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (ksPass != null) {
                signingConfig = signingConfigs.getByName("mv")
            }
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    // installs the pre-compiled (baseline) profiles that ship inside Compose, so the app starts and scrolls fast right after install
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
}
