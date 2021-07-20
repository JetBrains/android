android {
  defaultConfig {
    targetSdkVersion("S")
  }
  productFlavors {
    create("foo") {
      targetSdkVersion("R")
    }
  }
}
