plugins {
  id("org.jetbrains.kotlin.multiplatform")
  id("com.android.kotlin.multiplatform.library")
}

kotlin {
  androidLibrary {
    withJava()
    withAndroidTestOnJvmBuilder {
      compilationName = "unitTest"
      defaultSourceSetName = "androidUnitTest"
    }.configure {
      isIncludeAndroidResources = true
    }

    withAndroidTestOnDeviceBuilder {
      compilationName = "instrumentedTest"
      defaultSourceSetName = "androidInstrumentedTest"
    }

    compilations.withType(com.android.build.api.dsl.KotlinMultiplatformAndroidTestOnDeviceCompilation::class.java) {
      instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compilations.all {
      compilerOptions.configure {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
      }
    }

    namespace = "com.example.kmpfirstlib"
    compileSdk = 33
    minSdk = 22

    dependencyVariantSelection {
      buildTypes.add("debug")
      productFlavors.put("type", mutableListOf("typeone"))
      productFlavors.put("mode", mutableListOf("modetwo"))
    }

    aarMetadata.minAgpVersion = "7.2.0"
  }

  sourceSets.getByName("androidMain") {
    dependencies {
      api(project(":androidLib"))
      implementation(project(":kmpSecondLib"))
      implementation(project(":kmpJvmOnly"))
    }
  }

  sourceSets.getByName("androidInstrumentedTest") {
    dependencies {
      implementation("androidx.appcompat:appcompat:1.4.1")
      implementation("androidx.test:runner:1.4.0-alpha06")
      implementation("androidx.test:core:1.4.0-alpha06")
      implementation("androidx.test.ext:junit:1.1.2")
    }
  }

  sourceSets.getByName("commonTest") {
    dependencies {
      implementation(kotlin("test"))
    }
  }
}