pluginManagement {
  repositories {

  }
}
plugins {
  id("org.gradle.experimental.plugin-ecosystem").version("0.1.54")
}

dependencyResolutionManagement {
  repositories {

  }
}

include("plugins")
rootProject.name = "build-logic"
