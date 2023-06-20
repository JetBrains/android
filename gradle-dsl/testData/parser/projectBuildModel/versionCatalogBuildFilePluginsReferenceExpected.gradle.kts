plugins {
  alias(libs.plugins.app)
  alias(libs.plugins.lib) apply true
  alias(libs.plugins.com) apply false
}
dependencies {
  implementation(libs.adep)
}