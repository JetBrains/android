import com.android.build.gradle.AppPlugin
import com.android.build.api.dsl.ApplicationExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinAndroidPluginWrapper

apply<AppPlugin>()
apply<KotlinAndroidPluginWrapper>()

configure<ApplicationExtension> {
    namespace = "com.example.kotlingradle"
    @Suppress("DEPRECATION")
    compileSdkVersion(28)

    defaultConfig {
        @Suppress("DEPRECATION")
        minSdkVersion(15)
        @Suppress("DEPRECATION")
        targetSdkVersion(28)

        applicationId = "com.example.kotlingradle"
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles("proguard-rules.pro")
        }
    }
}

dependencies {
    "implementation"("com.android.support:appcompat-v7:28.0.0")
    "implementation"("com.android.support.constraint:constraint-layout:1.0.2")
    "implementation"(kotlin("stdlib", "1.4.32"))
    "implementation"(project(":lib"))
}

