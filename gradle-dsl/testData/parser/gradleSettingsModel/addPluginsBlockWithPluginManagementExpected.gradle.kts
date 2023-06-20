pluginManagement {
  plugins {
    id("com.android.application") version "7.0.0" apply false
  }
  repositories {
    jcenter()
  }
}
plugins {
  id("com.android.settings") version "7.4.0"
}
rootProject.name = "My Application"
include ":app"
