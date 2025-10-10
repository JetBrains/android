plugins {
  id("com.android.application")
}

android {
  namespace = "google.simpleapplication"
  compileSdkVersion(23)

  defaultConfig {
    targetSdkVersion(23)
    minSdkVersion(19)
    applicationId = "google.simpleapplication"
    versionCode = 1
    versionName = "1.0"
  }
  testOptions {
    suites {
      create("test") {
        useJunitEngine {
          inputs.add(com.android.build.api.dsl.AgpTestSuiteInputParameters.MERGED_MANIFEST)
          enginesDependencies("junit:junit:4.12")
        }
        assets {}
        targetVariants.add("debug")
        targets.create("t1") {
        }
      }
    }
  }
}

dependencies {
  implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
  api("com.android.support:appcompat-v7:+")
  api("com.google.guava:guava:19.0")
  api("com.android.support.constraint:constraint-layout:1.0.2")
  testImplementation("junit:junit:4.12")
  androidTestImplementation("com.android.support.test:runner:+")
  androidTestImplementation("com.android.support.test.espresso:espresso-core:+")
}
