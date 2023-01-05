pluginManagement {
  plugins {
    id("com.android.application") version "4.2.0"
    id("org.jetbrains.kotlin.android") version "1.4.10" apply false
  }
  repositories {
    google()
    jcenter()
  }
}
dependencyResolutionManagement {
  repositories {
    jcenter()
  }
}
rootProject.name = "My Application"
include(":app")
