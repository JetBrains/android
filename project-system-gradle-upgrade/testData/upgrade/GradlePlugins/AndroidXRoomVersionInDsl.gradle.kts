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
  id("androidx.room") version "2.6.0" apply false
}

allprojects {
  repositories {
    jcenter()
  }
}
