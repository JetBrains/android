buildscript {
  val androidGradleVersion by extra("0.11.0")
  dependencies {
    classpath("com.android.tools.build:gradle:$androidGradleVersion")
  }
}