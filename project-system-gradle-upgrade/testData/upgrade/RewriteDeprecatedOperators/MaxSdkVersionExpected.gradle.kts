android {
  defaultConfig {
    maxSdk = 29
  }
  productFlavors {
    create("foo") {
      maxSdk = 28
    }
  }
}
