buildscript {
  repositories {
    maven { url = uri("https://maven.fabric.io/public") }
    jcenter()
  }

  dependencies {
    classpath("com.android.tools.build:gradle:4.0.0")
    classpath("io.fabric.tools:gradle:1.31.2")
  }
}
