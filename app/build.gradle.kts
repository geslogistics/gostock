plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.gostock"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.gostock"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // ML Kit Barcode Scanning
    implementation("com.google.mlkit:barcode-scanning:17.2.0") // Or the latest stable version
    // CameraX dependencies for easy camera integration
    implementation("androidx.camera:camera-core:1.3.3") // Or the latest stable version
    implementation("androidx.camera:camera-camera2:1.3.3") // Or the latest stable version
    implementation("androidx.camera:camera-lifecycle:1.3.3") // Or the latest stable version
    implementation("androidx.camera:camera-view:1.3.3") // Or the latest stable version
    implementation("androidx.camera:camera-extensions:1.3.3") // Or the latest stable version

    // Gson for JSON serialization/deserialization
    implementation("com.google.code.gson:gson:2.10.1") // Or the latest stable version

    // RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.3.2") // Or latest stable version
    // CardView (often useful for list items)
    implementation("androidx.cardview:cardview:1.0.0") // Or latest stable version

    implementation("androidx.activity:activity-ktx:1.9.0") // Or latest stable
    implementation("androidx.fragment:fragment-ktx:1.6.0") // Or latest stable

    implementation("com.google.android.material:material:1.12.0")

    implementation(libs.material)

    // ... other dependencies ...
}