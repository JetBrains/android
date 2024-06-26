buildscript {
    val kotlin_version by extra("placeholder")
    repositories {
        jcenter()
        google()
    }
    dependencies {
        val nav_version = "2.4.1"
        classpath("com.android.tools.build:gradle:3.4.0")
        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:${nav_version}")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version")
    }
}

allprojects {
    repositories {
        jcenter()
    }
}
