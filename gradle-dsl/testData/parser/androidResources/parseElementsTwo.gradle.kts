android {
  androidResources {
    additionalParameters = listOf("abcd", "efgh")
    cruncherEnabled = false
    cruncherProcesses = 2
    failOnMissingConfigEntry = true
    ignoreAssetsPattern = "ijkl"
    noCompress("a", "b")
  }
}
