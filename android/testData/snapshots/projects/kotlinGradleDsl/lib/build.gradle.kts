plugins {
    id("com.android.library")
}

android {
    namespace = "com.example.lib"
    compileSdkVersion(27)

    defaultConfig {
        minSdkVersion(15)
    }

    lint {
      targetSdk = 27
    }

    testOptions {
      targetSdk = 27
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles("proguard-rules.pro")
        }
    }
}

dependencies {
}
