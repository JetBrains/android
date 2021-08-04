android {
  defaultConfig {
    manifestPlaceholders += mapOf("a" to "b")
  }
  buildTypes {
    create("mumble") {
      manifestPlaceholders += mapOf("c" to "d")
    }
  }
  productFlavors {
    create("foo") {
      manifestPlaceholders += mapOf("e" to "f", "g" to "h")
    }
  }
}