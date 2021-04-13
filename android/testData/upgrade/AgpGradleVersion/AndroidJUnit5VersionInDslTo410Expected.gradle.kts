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
  id("de.mannodermaus.android-junit5") version "1.6.1.0" apply false
}

allprojects {
    repositories {
        jcenter()
    }
}
