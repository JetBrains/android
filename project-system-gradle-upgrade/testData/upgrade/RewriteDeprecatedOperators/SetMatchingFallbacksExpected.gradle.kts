android {
  defaultConfig {
    matchingFallbacks += listOf("one", "two")
  }
  buildTypes {
    create("mumble") {
      matchingFallbacks += listOf("three")
    }
  }
  productFlavors {
    create("foo") {
      matchingFallbacks += listOf("four")
    }
  }
}