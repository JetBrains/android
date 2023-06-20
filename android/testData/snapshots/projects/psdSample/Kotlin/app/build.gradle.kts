plugins {
    id("com.android.application")
}

val myVariable by extra("26.1.0")
val variable1 by extra("1.3")
val anotherVariable by extra("3.0.1")
val varInt by extra(1)
val varBool by extra(true)
val varRefString by extra(variable1)
val varProGuardFiles by extra(listOf("proguard-rules.txt", "proguard-rules2.txt"))
val localList by extra(listOf("26.1.1", "56.2.0"))
val localMap by extra(mapOf("KTSApp" to "com.example.text.KTSApp", "LocalApp" to "com.android.localApp"))
extra["valVersion"] = 15
extra["versionVal"] = "28.0.0"

android {
    compileSdkVersion(19)

    dynamicFeatures = mutableSetOf(":dyn_feature")

    signingConfigs {
        create("myConfig") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "com.example.psd.sample.app.default"
        applicationIdSuffix = "defaultSuffix"
        testApplicationId = "com.example.psd.sample.app.default.test"
        maxSdkVersion(26)
        minSdkVersion(9)
        targetSdkVersion(19)
        versionCode = 1
        versionName = "1.0"
        versionNameSuffix = "vns"
        setManifestPlaceholders(mapOf("aa" to "aaa", "bb" to "bbb", "cc" to true))
        setTestFunctionalTest(false)
    }
    buildTypes {
        getByName("release") {
            applicationIdSuffix = "suffix"
            versionNameSuffix = "vsuffix"
            isDebuggable = false
            isJniDebuggable = false
            isMinifyEnabled = false
            renderscriptOptimLevel = 2
            signingConfig = signingConfigs.getByName("myConfig")
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.txt", "proguard-rules2.txt")
        }
        create("specialRelease") {
            matchingFallbacks = mutableListOf("release", "debug")
            versionNameSuffix = "vnsSpecial"
        }
    }
    flavorDimensions("foo", "bar")
    productFlavors {
        create("basic") {
            setDimension("foo")
            applicationId = "com.example.psd.sample.app"
        }
        create("paid") {
            setDimension("foo")
            applicationId = "com.example.psd.sample.app.paid"
            testApplicationId = "com.example.psd.sample.app.paid.test"
            maxSdkVersion(25)
            minSdkVersion(10)
            targetSdkVersion(20)
            versionCode = 2
            versionName = "2.0"
            versionNameSuffix = "vnsFoo"
            testInstrumentationRunnerArguments(mapOf("a" to "AAA", "b" to "BBB", "c" to "CCC"))
            setTestHandleProfiling(varBool)
            setTestFunctionalTest(rootProject.extra["rootBool"] as Boolean)
        }
        create("bar") {
            setDimension("bar")
            applicationIdSuffix = "barSuffix"
        }
        create("otherBar") {
            setDimension("bar")
            setMatchingFallbacks("bar")
            resConfig("en")
            resConfigs("hdpi", "xhdpi")
        }
    }
}

val moreVariable by extra("1234")
val mapVariable by extra(mapOf("a" to "\"double\" quotes", "b" to "'single' quotes"))

dependencies {
    api("com.android.support:appcompat-v7:+")
    implementation("com.android.support.constraint:constraint-layout:1.1.0")
    implementation("com.android.support.test:runner:1.0.2")
    implementation("com.android.support.test.espresso:espresso-core:3.0.2")
    api(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
}
