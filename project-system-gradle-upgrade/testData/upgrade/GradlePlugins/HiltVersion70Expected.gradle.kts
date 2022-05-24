buildscript {
    repositories {
        jcenter()
        google()
    }
    dependencies {
        val dagger_version = "2.38"
        classpath("com.android.tools.build:gradle:3.4.0")
        classpath("com.google.dagger:hilt-android-gradle-plugin:${dagger_version}")
    }
}

allprojects {
    repositories {
        jcenter()
    }
}
