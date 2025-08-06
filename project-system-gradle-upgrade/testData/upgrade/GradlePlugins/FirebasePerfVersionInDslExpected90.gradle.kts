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
    id("com.google.gms.google-services") version "4.3.10" apply false
    id("com.google.firebase.firebase-perf") version "2.0.0" apply false
}

allprojects {
    repositories {
        jcenter()
    }
}
