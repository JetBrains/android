android {
  defaultConfig {
    minSdkPreview = "S"
  }
  productFlavors {
    create("foo") {
      minSdkPreview = "R"
    }
  }
}
