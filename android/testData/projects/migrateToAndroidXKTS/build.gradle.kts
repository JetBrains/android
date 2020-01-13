buildscript {
  val kotlin_version by extra("to-be-replaced-by-AndroidGradleTests")
  val gradle_version by extra('+')
  repositories {

  }
  dependencies {
    classpath("com.android.tools.build:gradle:$gradle_version")
    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version")
  }
}

allprojects {
  repositories {
    // This will be populated by AndroidGradleTestCase
  }
}