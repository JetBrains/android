buildscript {
    repositories {
        jcenter()
        google()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:3.5.0")
        classpath("com.google.firebase:firebase-appdistribution-gradle:1.4.0")
    }
}

allprojects {
    repositories {
        jcenter()
    }
}
