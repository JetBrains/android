buildscript {
    repositories {
        jcenter()
        google()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:3.4.0")
    }
}

plugins {
  id("androidx.navigation.safeargs.kotlin") version "2.9.5" apply false
}

allprojects {
    repositories {
        jcenter()
    }
}
