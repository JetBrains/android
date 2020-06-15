plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("android.extensions")
}

android {
    compileSdkVersion(27)
    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdkVersion(27)
        targetSdkVersion(27)
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }

    flavorDimensions("color")

    productFlavors {
        create("red") {
            setDimension("color")
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }
}

val testVariable = "androidx.exifinterface:exifinterface:1.0.0-alpha1"

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlin_version")
    implementation("androidx.constraint:base:2.0.0-alpha1")
    implementation("androidx.recyclerview:recyclerview:1.0.0-alpha1")
    testImplementation("junit:junit:4.12")
    androidTestImplementation("androidx.test:runner:1.1.0-alpha1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.1.0-alpha1")
    implementation(mapOf("group" to "androidx.appcompat", "name" to "base", "version" to "1.0.0-alpha1"))
    implementation(testVariable)
    implementation("androidx.core:core-ktx:1.0.0-alpha1")
    implementation("androidx.core:newer-core-ktx:2.0.0-alpha1")
    implementation("androidx.core:newer-version-ktx:1.2.0")
    implementation("androidx.core:variable-ktx:1.0.0-alpha1")
}
