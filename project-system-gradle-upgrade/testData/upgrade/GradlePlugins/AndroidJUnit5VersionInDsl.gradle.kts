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
  id("de.mannodermaus.android-junit5") version "1.4.2.2" apply false
}

allprojects {
    repositories {
        jcenter()
    }
}
