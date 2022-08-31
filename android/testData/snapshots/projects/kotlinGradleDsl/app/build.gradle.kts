plugins {
    id("org.jetbrains.kotlin.android")
    id("com.android.application")
}

android {
    buildToolsVersion("28.0.3")
    compileSdkVersion(28)

    defaultConfig {
        minSdkVersion(15)
        targetSdkVersion(28)

        applicationId = "com.example.kotlingradle"
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles("proguard-rules.pro")
        }
    }
}

dependencies {
    "implementation"("com.android.support:appcompat-v7:28.0.0")
    "implementation"("com.android.support.constraint:constraint-layout:1.0.2")
    "implementation"(kotlin("stdlib", "1.3.61"))
    "implementation"(project(":lib"))
}

