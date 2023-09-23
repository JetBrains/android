buildscript {
  val kotlin_version by extra("$VERSION$")
  repositories {
    google()
  }
  dependencies {
    classpath("com.android.tools.build:gradle:4.1.0")
    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version")
  }
}
