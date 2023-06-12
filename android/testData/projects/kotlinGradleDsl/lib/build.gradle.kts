import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.LibraryExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinAndroidPluginWrapper

apply {
    plugin<LibraryPlugin>()
    plugin<KotlinAndroidPluginWrapper>()
}

configure<LibraryExtension> {
    namespace = "com.example.lib"
    compileSdkVersion(27)

    defaultConfig {
        minSdkVersion(15)
        targetSdkVersion(27)
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
