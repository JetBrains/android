android {
  defaultConfig {
    setManifestPlaceholders(mapOf("a" to "b"))
  }
  buildTypes {
    create("mumble") {
      setManifestPlaceholders(mapOf("c" to "d"))
    }
  }
  productFlavors {
    create("foo") {
      setManifestPlaceholders(mapOf("e" to "f", "g" to "h"))
    }
  }
}