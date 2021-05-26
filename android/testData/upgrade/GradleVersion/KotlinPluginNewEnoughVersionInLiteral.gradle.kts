buildscript {
    repositories {
        jcenter()
        google()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:3.4.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.4.10")
    }
}

allprojects {
    repositories {
        jcenter()
    }
}
