plugins {
    id("com.android.application")
    id("kotlin-android")
}

android {
    compileSdkVersion(29)
    buildToolsVersion("29.0.3")

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdkVersion(16)
        targetSdkVersion(29)
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            minifyEnabled(false)
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    flavorDimensions("status")
    productFlavors {
        create("paid") {
            dimension = "status"
        }
        create("free") {
            dimension = "status"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

configurations {
    create("paidReleaseCompile") {
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${rootProject.extra["kotlin_version"]}")
    implementation("androidx.core:core-ktx:1.3.0")
    implementation("androidx.appcompat:appcompat:1.1.0")
    "paidReleaseCompile"("com.google.android.material:material:1.1.0")
    releaseCompile("androidx.constraintlayout:constraintlayout:1.1.3")
    compile("androidx.navigation:navigation-fragment-ktx:2.2.2")
    api("androidx.navigation:navigation-ui-ktx:2.2.2")
    testCompile("junit:junit:4.13")
    androidTestCompile("androidx.test.ext:junit:1.1.2")
    androidTestRuntime("androidx.test.espresso:espresso-core:3.2.0")
}
