android {
  defaultConfig {
    resourceConfigurations += setOf("en", "fr")
  }
  productFlavors {
    create("foo") {
      resourceConfigurations += setOf("pt")
    }
  }
}
