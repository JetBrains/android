buildscript {
    repositories {
        jcenter()
        google()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:3.4.0")
        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.2.0")
    }
}

allprojects {
    repositories {
        jcenter()
    }
}
