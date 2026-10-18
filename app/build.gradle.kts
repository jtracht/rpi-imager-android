import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}
val upstreamInfo = Properties().apply { rootProject.file("upstream.properties").inputStream().use { load(it) } }
val releaseStore = System.getenv("ANDROID_KEYSTORE_PATH")
android {
    namespace = "de.jtracht.piimager"
    compileSdk = 36
    defaultConfig {
        applicationId = "de.jtracht.piimager"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.environmentVariable("ANDROID_VERSION_CODE").orElse("1").get().toInt()
        versionName = providers.environmentVariable("ANDROID_VERSION_NAME").orElse("0.2.0").get()
        buildConfigField("String", "UPSTREAM_TAG", "\"${upstreamInfo.getProperty("tag")}\"")
        buildConfigField("String", "UPSTREAM_COMMIT", "\"${upstreamInfo.getProperty("commit")}\"")
    }
    signingConfigs {
        if (releaseStore != null) create("distribution") {
            storeFile = file(releaseStore)
            storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("ANDROID_KEY_ALIAS")
            keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            if (releaseStore != null) signingConfig = signingConfigs.getByName("distribution")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    // The FAT implementation only uses Android Log; image/crypto tests run on the JVM.
    testOptions { unitTests.isReturnDefaultValues = true }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // Our Android USB Host transport does not use these legacy JNI helpers.
        jniLibs.excludes += setOf("**/liberrno-lib.so", "**/libusb-lib.so")
    }
}
dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.coroutines.android)
    implementation(libs.libaums)
    implementation(libs.xz)
    implementation(libs.codec)
    testImplementation(libs.junit)
}
