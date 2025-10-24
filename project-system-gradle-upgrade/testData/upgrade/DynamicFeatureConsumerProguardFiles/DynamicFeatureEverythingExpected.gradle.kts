plugins {
    id("com.android.dynamic-feature")
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
        dimension = "dimension"
        minSdkVersion(21)
        maxSdkVersion(30)
        multiDexEnabled = true
        targetSdkVersion(29)
        versionCode = 30
        versionName = "vN"
        versionNameSuffix = ".vNS"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        proguardFiles("cPF1.txt")
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
            proguardFiles(
		getDefaultProguardFile("proguard-android-optimize.txt"),
	        "proguard-rules.pro",
                "release-cPF.txt"
            )
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
	    proguardFiles("xyz-cPF1.txt", "xyz-cPF2.txt")
        }
    }
}

