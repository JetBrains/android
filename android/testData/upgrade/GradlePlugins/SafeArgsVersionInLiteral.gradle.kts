buildscript {
    ext.kotlin_version = 'placeholder'
    repositories {
        jcenter()
        google()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:3.4.0")
        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.2.0")
        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version"
    }
}

allprojects {
    repositories {
        jcenter()
    }
}
