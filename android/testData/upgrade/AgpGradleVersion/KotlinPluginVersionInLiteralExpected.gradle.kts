buildscript {
    repositories {
        jcenter()
        google()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:3.4.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.3.20")
    }
}

allprojects {
    repositories {
        jcenter()
    }
}
