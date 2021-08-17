buildscript {
    repositories {
        jcenter()
        google()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:3.6.0")
    }
}

plugins {
  id("com.google.firebase.crashlytics") version "2.5.0" apply false
}

allprojects {
    repositories {
        jcenter()
    }
}
