android {
  defaultConfig {
    targetSdkVersion(29)
  }
  productFlavors {
    create("foo") {
      targetSdkVersion(28)
    }
  }
}
