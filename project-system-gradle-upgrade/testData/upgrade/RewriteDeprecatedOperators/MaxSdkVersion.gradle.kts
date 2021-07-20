android {
  defaultConfig {
    maxSdkVersion(29)
  }
  productFlavors {
    create("foo") {
      maxSdkVersion(28)
    }
  }
}
