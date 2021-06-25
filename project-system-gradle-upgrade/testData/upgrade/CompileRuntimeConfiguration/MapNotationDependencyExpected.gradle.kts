plugins {
    id("com.android.application")
    id("kotlin-android")
}

android {
    compileSdkVersion(29)
    buildToolsVersion = "29.0.3"

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdkVersion(16)
        targetSdkVersion(29)
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version")
    implementation("androidx.core:core-ktx:1.2.0")
    implementation("androidx.appcompat:appcompat:1.1.0")
    implementation(group="com.google.android.material", name="material", version="1.1.0")
    releaseImplementation(group="androidx.constraintlayout", name="constraintlayout", version="1.1.3")
    implementation(group="androidx.navigation", name="navigation-fragment-ktx", version="2.2.1")
    api(group="androidx.navigation", name="navigation-ui-ktx", version="2.2.1")
    testImplementation(group="junit", name="junit", version="4.13")
    androidTestImplementation(group="androidx.test.ext", name="junit", version="1.1.1")
    androidTestRuntimeOnly(group="androidx.test.espresso", name="espresso-core", version="3.2.0")
}
