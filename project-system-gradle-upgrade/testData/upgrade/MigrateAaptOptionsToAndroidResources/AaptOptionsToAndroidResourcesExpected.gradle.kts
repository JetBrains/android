android {
  androidResources {
    ignoreAssetsPattern = "a"
    noCompress += listOf("test")
    failOnMissingConfigEntry = true
    additionalParameters += listOf("a")
    namespaced = false
  }
}