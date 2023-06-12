android {
  productFlavors {
    create("free") {
      applicationIdSuffix = ".free"
      buildConfigField("abcd", "efgh", "ijkl")
    }
    create("paid") {
      initWith(productFlavors.getByName("free"))
      applicationIdSuffix = ".paid"
    }
  }
}