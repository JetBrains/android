buildscript {
  repositories {
    jcenter()
    google()
  }
  dependencies {
    classpath("com.android.tools.build:gradle:3.6.0")
    classpath("androidx.room:androidx.room.gradle.plugin:2.6.0")
  }
}

allprojects {
  repositories {
    jcenter()
  }
}
