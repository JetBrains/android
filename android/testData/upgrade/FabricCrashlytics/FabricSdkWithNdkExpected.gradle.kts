plugins {
  id("com.android.application")
  id("com.google.firebase.crashlytics")
  id("com.google.gms.google-services")
}

android {
  buildTypes {
    getByName("release") {
      firebaseCrashlytics {
        nativeSymbolUploadEnabled = true
      }
    }
  }
}

dependencies {
  implementation("com.google.firebase:firebase-crashlytics:17.2.1")
  implementation("com.google.firebase:firebase-analytics:17.5.0")
  implementation("com.google.firebase:firebase-crashlytics-ndk:17.2.1")
  testImplementation("org.junit:junit:4.11")
}
