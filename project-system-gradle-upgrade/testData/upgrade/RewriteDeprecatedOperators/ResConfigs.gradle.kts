android {
  defaultConfig {
    resConfigs("en", "fr")
  }
  productFlavors {
    create("foo") {
      resConfig("pt")
    }
  }
}
