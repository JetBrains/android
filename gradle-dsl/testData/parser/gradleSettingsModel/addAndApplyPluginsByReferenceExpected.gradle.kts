pluginManagement {
  plugins {
    alias(libs.plugins.foo)
    alias(libs.plugins.bar) apply true
  }
}
plugins {
  alias(libs.plugins.foo) apply false
  alias(libs.plugins.bar)
}
dependencyResolutionManagement {
  repositories {
    jcenter()
  }
}
rootProject.name = "My Application"
include(":app")
