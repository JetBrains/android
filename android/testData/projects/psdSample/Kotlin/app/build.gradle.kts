plugins {
    id("com.android.application")
}

extra["myVariable"] = "26.1.0"
extra["variable1"] = "1.3"
extra["anotherVariable"] = "3.0.1"
extra["varInt"] = 1
extra["varBool"] = true
extra["varRefString"] = extra["variable1"]
extra["varProGuardFiles"] = listOf("proguard-rules.txt", "proguard-rules2.txt")
extra["localList"] = listOf("26.1.1", "56.2.0")
extra["localMap"] = mapOf("KTSApp" to "com.example.text.KTSApp", "LocalApp" to "com.android.localApp")
extra["valVersion"] = 15
extra["versionVal"] = "28.0.0"

android {
    compileSdk = 19

    dynamicFeatures += setOf(":dyn_feature")

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
        maxSdk = 26
        minSdk = 9
        targetSdk = 19
        versionCode = 1
        versionName = "1.0"
        versionNameSuffix = "vns"
        manifestPlaceholders += mapOf("aa" to "aaa", "bb" to "bbb", "cc" to true)
        testFunctionalTest = false
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
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.txt", "proguard-rules2.txt")
        }
        create("specialRelease") {
            matchingFallbacks += listOf("release", "debug")
            versionNameSuffix = "vnsSpecial"
        }
        debug {
            isPseudoLocalesEnabled = true
        }
    }
    flavorDimensions += listOf("foo", "bar")
    productFlavors {
        create("basic") {
            dimension = "foo"
            applicationId = "com.example.psd.sample.app"
        }
        create("paid") {
            dimension = "foo"
            applicationId = "com.example.psd.sample.app.paid"
            testApplicationId = "com.example.psd.sample.app.paid.test"
            maxSdk = 25
            minSdk = 10
            targetSdk = 20
            versionCode = 2
            versionName = "2.0"
            versionNameSuffix = "vnsFoo"
            testInstrumentationRunnerArguments(mapOf("a" to "AAA", "b" to "BBB", "c" to "CCC"))
            testHandleProfiling = project.extra["varBool"] as Boolean
            testFunctionalTest = rootProject.extra["rootBool"] as Boolean
        }
        create("bar") {
            dimension = "bar"
            applicationIdSuffix = "barSuffix"
        }
        create("otherBar") {
            dimension = "bar"
            matchingFallbacks += listOf("bar")
            resourceConfigurations += listOf("en", "hdpi", "xhdpi")
        }
    }
}

extra["moreVariable"] = "1234"
extra["mapVariable"] = mapOf("a" to "\"double\" quotes", "b" to "'single' quotes")

dependencies {
    api("com.android.support:appcompat-v7:+")
    implementation("com.android.support.constraint:constraint-layout:1.1.0")
    implementation("com.android.support.test:runner:1.0.2")
    implementation("com.android.support.test.espresso:espresso-core:3.0.2")
    api(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
}