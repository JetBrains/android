pluginManagement {
  repositories {
    google()
    mavenCentral()
  }
}

plugins {
  id("com.android.settings") version "7.4.0"
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
}

include(":app")
