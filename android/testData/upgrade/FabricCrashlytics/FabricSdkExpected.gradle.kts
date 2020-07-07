plugins {
  id("com.android.application")
  id("com.google.firebase.crashlytics")
  id("com.google.gms.google-services")
}

dependencies {
  implementation("com.google.firebase:firebase-crashlytics:17.2.1")
  implementation("com.google.firebase:firebase-analytics:17.5.0")
  testImplementation("org.junit:junit:4.11")
}
