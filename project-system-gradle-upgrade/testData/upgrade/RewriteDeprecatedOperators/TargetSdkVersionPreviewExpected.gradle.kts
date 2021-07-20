android {
  defaultConfig {
    targetSdkPreview = "S"
  }
  productFlavors {
    create("foo") {
      targetSdkPreview = "R"
    }
  }
}
