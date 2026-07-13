import com.android.build.gradle.LibraryPlugin
import com.android.build.api.dsl.LibraryExtension

apply {
    plugin<LibraryPlugin>()
}

configure<LibraryExtension> {
    namespace = "com.example.lib"
    @Suppress("DEPRECATION")
    compileSdkVersion(27)

    defaultConfig {
        @Suppress("DEPRECATION")
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
    "implementation"(kotlin("stdlib", "1.4.32"))
}
