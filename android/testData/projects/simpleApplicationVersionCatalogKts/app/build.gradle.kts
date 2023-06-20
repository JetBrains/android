plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlinAndroid)
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
}

dependencies {
  implementation(fileTree(mapOf("dir" to "libs", "include" to  listOf("*.jar"))))
  api("com.android.support:appcompat-v7:+")
  api(libs.guava)
  api(libs.constraint.layout)
  api(libs.bundles.both)
  testImplementation(libsTest.junit)
  androidTestImplementation("com.android.support.test:runner:+")
  androidTestImplementation("com.android.support.test.espresso:espresso-core:+")
}
