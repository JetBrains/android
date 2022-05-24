buildscript {
    repositories {
        jcenter()
        google()
    }
    dependencies {
        val firebase_perf_version = "1.4.1"
        classpath("com.android.tools.build:gradle:3.4.0")
        classpath("com.google.gms:google-services:4.3.10")
        classpath("com.google.firebase:perf-plugin:${firebase_perf_version}")
    }
}

allprojects {
    repositories {
        jcenter()
    }
}
