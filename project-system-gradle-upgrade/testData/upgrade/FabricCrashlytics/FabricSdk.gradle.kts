plugins {
  id("com.android.application")
  id("io.fabric")
}

dependencies {
  implementation("com.crashlytics.sdk.android:crashlytics:2.10.1")
  testImplementation("org.junit:junit:4.11")
}
