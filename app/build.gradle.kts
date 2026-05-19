plugins {
    id("com.android.application")
    id("kotlin-android")
}


android {
    namespace = "com.example.robloxkeeper"  // <<< 新增這行，解決錯誤
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.robloxkeeper"  // Update if needed
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17  // Updated for modern JDK
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx.v1131)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.work.runtime.ktx)
}