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
  id("org.jetbrains.kotlin.android") version "1.4.10" apply false
}

allprojects {
    repositories {
        jcenter()
    }
}
