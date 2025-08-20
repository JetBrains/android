plugins {
  id("org.jetbrains.kotlin.multiplatform")
  id("com.android.kotlin.multiplatform.library")
}

kotlin {
  androidLibrary {
    withJava()
    withHostTestBuilder {
      compilationName = "unitTest"
      defaultSourceSetName = "androidUnitTest"
    }.configure {
      isIncludeAndroidResources = true
    }

    withDeviceTestBuilder {
      compilationName = "instrumentedTest"
      defaultSourceSetName = "androidInstrumentedTest"
    }

    compilations.withType(com.android.build.api.dsl.KotlinMultiplatformAndroidDeviceTestCompilation::class.java) {
      instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compilations.configureEach {
      compileTaskProvider.configure {
        compilerOptions {
          jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
        }
      }
    }

    namespace = "com.example.kmpfirstlib"
    compileSdk = 33
    minSdk = 22

    localDependencySelection {
      selectBuildTypeFrom.add("debug")
      productFlavorDimension("type") {
        selectFrom.set(listOf("typeone"))
      }
      productFlavorDimension("mode") {
        selectFrom.set(listOf("modetwo"))
      }
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