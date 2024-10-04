android {
  productFlavors {
    productFlavor("dependent") {
      applicationIdSuffix = ".dependent"
    }
    productFlavor("demo") {
      initWith(productFlavor("dependent"))
    }
  }
}