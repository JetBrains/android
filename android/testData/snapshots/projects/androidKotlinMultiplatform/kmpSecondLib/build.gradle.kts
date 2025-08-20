plugins {
  id("kotlin-multiplatform")
  id("com.android.kotlin.multiplatform.library")
}

kotlin {
  jvmToolchain {
    languageVersion.set(JavaLanguageVersion.of(11))
  }

  jvm {
    compilations.configureEach {
      compileTaskProvider.configure {
        compilerOptions {
          jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
        }
      }
    }
  }

  targets.withType(com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget::class.java) {
    namespace = "com.example.kmpsecondlib"
    compileSdk = 33
    minSdk = 22

    compilations.configureEach {
      compileTaskProvider.configure {
        compilerOptions {
          jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
        }
      }
    }
  }
}