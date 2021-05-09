plugins {
    id("com.android.test")
}

android {
    compileSdkVersion(30)
    buildToolsVersion = "30.0.3"
    aidlPackagedList += listOf("one.aidl", "two.aidl")
    assetPacks += listOf(":ap1", ":ap2")
    dynamicFeatures += listOf(":df1", ":df2")
    targetProjectPath = ":tpp"

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
        consumerProguardFiles(getDefaultProguardFile("cPF1.txt"))
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
            consumerProguardFiles("release-cPF.txt")
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
            consumerProguardFiles(listOf("xyz-cPF1.txt", "xyz-cPF2.txt"))
            isDefault = true
            multiDexEnabled = true
        }
    }
}

