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

// ag/19457336 says that the following line should not be removed
// ("This is used to ensure that the class which is searched for in SyncedProjectTest still exists.")
// However, the KotlinJvmCompilerArgumentsProvider class has been removed in KGP 2.1.0-Beta1
// (https://github.com/JetBrains/kotlin/commit/1c95429c4), so the following line no longer compiles.
// Therefore, we need to comment out this line
// for SyncedProjectsAllAgpTest.testKotlinGradleDsl[Agp(8.0.2, g=8.0, k=2.1.0-Beta1 , m=V2)] to pass.
//
// println(org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompilerArgumentsProvider::class.java.simpleName)