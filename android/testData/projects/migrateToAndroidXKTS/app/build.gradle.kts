plugins {
  id("com.android.application")
  kotlin("android")
}
apply(plugin = "com.android.application")

val version_27 by extra(27)

android {
  compileSdkVersion(27)

  defaultConfig {
    targetSdkVersion(27)
    minSdkVersion(15)
    applicationId = "com.example.google.migratetoandroidxkts"
    versionCode = 1
    versionName = "1.0"
  }
}

val testVariable = "com.android.support:design:+"

dependencies {
  implementation(fileTree(mapOf("dir" to "libs", "include" to  listOf("*.jar"))))
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.0-Beta") // TODO(263236464): Revert to `+` instead of `1.8.0-Beta`
  implementation(mapOf("group" to "com.android.support", "name" to "appcompat-v7", "version" to "+"))
  implementation("com.android.support.constraint:constraint-layout:+")
  implementation(testVariable)
}
