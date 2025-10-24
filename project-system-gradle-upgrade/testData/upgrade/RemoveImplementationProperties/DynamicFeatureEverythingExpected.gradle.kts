plugins {
    id("com.android.dynamic-feature")
}

android {
    compileSdkVersion(30)
    buildToolsVersion = "30.0.3"

    defaultConfig {
        minSdkVersion(21)
	consumerProguardFiles(getDefaultProguardFile("cPF1.txt"))
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            isCrunchPngs = true
            isMinifyEnabled = false
	    consumerProguardFiles("release-cPF.txt")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    productFlavors {
        create("xyz") {
            consumerProguardFiles(listOf("xyz-cPF1.txt","xyz-cPF2.txt"))
        }
    }
}

