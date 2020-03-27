android {
  productFlavors {
    create("demo") {
      matchingFallbacks = mutableListOf("trial")
    }
  }
}
