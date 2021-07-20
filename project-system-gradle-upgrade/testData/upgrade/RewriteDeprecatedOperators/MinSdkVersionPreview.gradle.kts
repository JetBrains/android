android {
  defaultConfig {
    minSdkVersion("S")
  }
  productFlavors {
    create("foo") {
      minSdkVersion("R")
    }
  }
}
