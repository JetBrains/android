plugins {
  id("com.android.application")
}
android {
  namespace = "com.example.app"

  compileSdk = 33

  defaultConfig {
    minSdk = 22
    targetSdk = 33
    missingDimensionStrategy("type", "typeone")
    missingDimensionStrategy("mode", "modetwo")
  }
}

dependencies {
    implementation(project(":kmpFirstLib"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.truth:truth:0.44")
}
