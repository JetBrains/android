plugins {
    id("com.android.library")
}

android {
    compileSdkVersion(30)
    buildToolsVersion = "30.0.3"
    aidlPackagedList += listOf("one.aidl", "two.aidl")

    defaultConfig {
        minSdkVersion(21)
        multiDexEnabled = true
        targetSdkVersion(29)
        consumerProguardFiles(getDefaultProguardFile("cPF1.txt"))
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            isDefault = true
            isMinifyEnabled = false
            consumerProguardFiles("release-cPF.txt")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    productFlavors {
        create("xyz") {
            consumerProguardFiles(listOf("xyz-cPF1.txt", "xyz-cPF2.txt"))
            isDefault = true
            multiDexEnabled = true
        }
    }
}

