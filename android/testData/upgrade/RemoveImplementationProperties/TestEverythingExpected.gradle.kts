plugins {
    id("com.android.test")
}

android {
    compileSdkVersion(30)
    buildToolsVersion = "30.0.3"
    targetProjectPath = ":tpp"

    defaultConfig {
        minSdkVersion(21)
        maxSdkVersion(30)
        multiDexEnabled = true
        targetSdkVersion(29)
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            isCrunchPngs = true
            isDebuggable = false
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    productFlavors {
        create("xyz") {
            multiDexEnabled = true
        }
    }
}

