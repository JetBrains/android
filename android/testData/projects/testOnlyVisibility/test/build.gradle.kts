plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "com.example.mylibrary"
    compileSdkVersion(36)
    targetProjectPath = ":app"

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
}