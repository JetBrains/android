android {
  defaultConfig {
    setMatchingFallbacks(listOf("one", "two"))
  }
  buildTypes {
    create("mumble") {
      setMatchingFallbacks(listOf("three"))
    }
  }
  productFlavors {
    create("foo") {
      setMatchingFallbacks("four")
    }
  }
}