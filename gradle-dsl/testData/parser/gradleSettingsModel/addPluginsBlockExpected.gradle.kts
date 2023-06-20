plugins {
  id("com.android.settings") version "7.4.0"
}
dependencyResolutionManagement {
  repositories {
    google()
    jcenter()
  }
}
rootProject.name = "My Application"
include(":app")
