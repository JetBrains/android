android {
  defaultConfig {
    targetSdk = 29
  }
  productFlavors {
    create("foo") {
      targetSdk = 28
    }
  }
}
