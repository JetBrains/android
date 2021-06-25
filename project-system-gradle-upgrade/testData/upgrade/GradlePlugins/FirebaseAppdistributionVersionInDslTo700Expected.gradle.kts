buildscript {
    repositories {
        jcenter()
        google()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:3.5.0")
    }
}

plugins {
  id("com.google.firebase.appdistribution") version "2.1.1" apply false
}

allprojects {
    repositories {
        jcenter()
    }
}
