buildscript {
    repositories {
        jcenter()
        google()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:3.6.0")
        classpath("de.mannodermaus.gradle.plugins:android-junit5:1.6.1.0")
    }
}

allprojects {
    repositories {
        jcenter()
    }
}
