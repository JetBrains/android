plugins {
  id("com.android.application")
  id("io.fabric")
}

android {
}

dependencies {
  implementation("com.crashlytics.sdk.android:crashlytics:2.10.1")
  implementation("com.crashlytics.sdk.android:crashlytics-ndk:2.1.1")
  testImplementation("org.junit:junit:4.11")
}

crashlytics {
  enableNdk = true
}
