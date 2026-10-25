plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Version is derived from the release tag in CI (passed via -PappVersionName=vX.Y.Z or the
// APP_VERSION_NAME env var). Local builds fall back to the dev version below.
val fallbackVersionName = "0.0.1"

fun resolveVersionName(): String {
    val provided = (project.findProperty("appVersionName") as String?)
        ?: System.getenv("APP_VERSION_NAME")
    return provided?.trim()?.removePrefix("v")?.takeIf { it.isNotEmpty() } ?: fallbackVersionName
}

// Maps a semver name (e.g. "1.2.3") to a monotonically increasing integer (10203).
fun resolveVersionCode(versionName: String): Int {
    val core = versionName.substringBefore("-")
    val parts = core.split(".")
    val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
    return major * 10000 + minor * 100 + patch
}

val appVersionName = resolveVersionName()
val appVersionCode = resolveVersionCode(appVersionName)

android {
    namespace = "com.noobexon.xposedfakelocation"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.noobexon.xposedfakelocation"
        minSdk = 29
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs["debug"]
        }

    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }


    packaging {
        resources {
            merges += "META-INF/xposed/*"
        }
    }

    kotlinOptions {
        jvmTarget = "21"
        freeCompilerArgs = listOf("-XXLanguage:+PropertyParamAnnotationDefaultTargetMode")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.osmdroid.android)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.line.awesome.android)
    implementation(libs.font.awesome)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.gson)
    implementation(libs.hiddenapibypass)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
}