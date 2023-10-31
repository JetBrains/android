plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.etrtestproject"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.example.etrtestproject"
        minSdk = 24
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation ("com.android.support:support-compat:28.0.0")
    implementation ("com.android.support:support-v4:28.0.0")
    testImplementation ("junit:junit:4.13.2")
    androidTestImplementation ("com.android.support.test.espresso:espresso-core:3.0.2")
    androidTestImplementation ("com.android.support.test.espresso:espresso-contrib:3.0.2")
    androidTestImplementation("com.android.support.test:rules:1.0.2")
}