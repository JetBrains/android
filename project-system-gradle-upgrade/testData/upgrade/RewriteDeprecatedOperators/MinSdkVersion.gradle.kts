android {
  defaultConfig {
    minSdkVersion(29)
  }
  productFlavors {
    create("foo") {
      minSdkVersion(28)
    }
  }
}
