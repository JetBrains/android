buildscript {
    repositories {
        jcenter()
        google()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:3.6.0")
        classpath("com.google.firebase:firebase-crashlytics-gradle:2.5.2")
    }
}

allprojects {
    repositories {
        jcenter()
    }
}
