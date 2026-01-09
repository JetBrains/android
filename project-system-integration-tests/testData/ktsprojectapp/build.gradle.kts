plugins {
  id("com.android.application") version "8.1.0"
  id("org.jetbrains.kotlin.android") version "2.2.10"
}

android {
  compileSdk = 33
    namespace = "com.example.ktsprojectapp"
    defaultConfig {
      minSdk = 24
    }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
  }
}

dependencies {}
