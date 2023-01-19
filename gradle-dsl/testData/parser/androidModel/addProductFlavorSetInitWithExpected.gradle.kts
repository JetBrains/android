android {
  productFlavors {
    create("dependent") {
      applicationIdSuffix = ".dependent"
    }
    create("demo") {
      initWith(productFlavors.getByName("dependent"))
    }
  }
}