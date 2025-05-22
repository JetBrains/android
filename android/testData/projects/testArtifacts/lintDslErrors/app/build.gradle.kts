plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.example.myapplication"
    compileSdk = 34

    defaultConfig {
        kotlinOptions { // Wrong place: options
            jvmTarget = "11"
        }
        applicationId = "com.example.myapplication.id"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

   flavorDimensions += "version"

    productFlavors {
        create("demo") {
            dimension = "version"
            dependencies { // Wrong place: product flavor
                implementation("com.android.support:appcompat-v7:28.0.0")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            dependencies { // Wrong place: build type
                implementation("com.android.support:appcompat-v7:28.0.0")
            }
        }
    }
    dependencies { // Wrong place: elsewhere
        implementation("com.android.support:appcompat-v7:28.0.0")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies { // OK
    implementation("com.android.support:appcompat-v7:28.0.0")
}
