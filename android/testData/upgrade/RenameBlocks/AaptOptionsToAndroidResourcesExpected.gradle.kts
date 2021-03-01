android {
  androidResources {
    ignoreAssetsPattern = "a"
    noCompress("test")
    failOnMissingConfigEntry = true
    additionalParameters = listOf("a")
    namespaced = false
  }
}