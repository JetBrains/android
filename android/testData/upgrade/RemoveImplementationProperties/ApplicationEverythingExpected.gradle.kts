plugins {
    id("com.android.application")
}

android {
    compileSdkVersion(30)
    buildToolsVersion = "30.0.3"
    assetPacks += listOf(":ap1", ":ap2")
    dynamicFeatures += listOf(":df1", ":df2")

    defaultConfig {
        applicationId = "aId"
        applicationIdSuffix = ".aIdS"
        minSdkVersion(21)
        maxSdkVersion(30)
        multiDexEnabled = true
        targetSdkVersion(29)
        versionCode = 30
        versionName = "vN"
        versionNameSuffix = ".vNS"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            isCrunchPngs = true
            isDebuggable = false
            isDefault = true
            isEmbedMicroApp = false
            isMinifyEnabled = false
            applicationIdSuffix = ".release-aIdS"
            versionNameSuffix = ".release-vNS"
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    productFlavors {
        create("xyz") {
            applicationId = "xyz-aId"
            applicationIdSuffix = ".xyz-aIdS"
            versionCode = 30
            versionName = "xyz-vN"
            versionNameSuffix = "xyz-vNS"
            isDefault = true
            multiDexEnabled = true
        }
    }
}

