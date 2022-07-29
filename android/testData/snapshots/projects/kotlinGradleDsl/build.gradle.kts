buildscript {
  repositories {
  }
  dependencies {
    classpath("com.android.tools.build:gradle:+")
    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:+")
  }
}

allprojects {
  repositories {
  }
}


// Do not remove. This is used to ensure that the class which is searched for in SyncedProjectTest still exists.

println(org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompilerArgumentsProvider::class.java.simpleName)