plugins {
    id("org.jetbrains.kotlin.android")
    id("com.android.library")
}

android {
    compileSdkVersion(27)

    defaultConfig {
        minSdkVersion(15)
        targetSdkVersion(27)
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles("proguard-rules.pro")
        }
    }
}

dependencies {
    "implementation"(kotlin("stdlib", "1.3.31"))
}
