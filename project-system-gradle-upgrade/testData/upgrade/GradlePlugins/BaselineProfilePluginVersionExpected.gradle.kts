buildscript {
    repositories {
        jcenter()
        google()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.0.0")
    }
}

plugins {
  id("androidx.baselineprofile") version "1.2.4" apply false
}

allprojects {
    repositories {
        jcenter()
    }
}
