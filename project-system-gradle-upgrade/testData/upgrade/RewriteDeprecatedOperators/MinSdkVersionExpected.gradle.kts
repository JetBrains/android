android {
  defaultConfig {
    minSdk = 29
  }
  productFlavors {
    create("foo") {
      minSdk = 28
    }
  }
}
