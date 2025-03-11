plugins {
  id("com.android.application")
  kotlin("android")
  kotlin("android.extensions")
}

apply(plugin = "com.android.application")

android {
  namespace = "com.example"
  compileSdk = 34
}

dependencies {
  api("com.android.support:appcompat-v7:+")
  api("com.android.support:cardview-v7:+")
  api("com.google.guava:guava:19.0")
  api("com.android.support.constraint:constraint-layout:1.0.2")
  implementation(group = "com.google.guava", name = "guava", version = "19.0")
  testImplementation("junit:junit:4.12")
  androidTestImplementation("com.android.support.test:runner:+")
  androidTestImplementation("com.android.support.test.espresso:espresso-core:+")
}
