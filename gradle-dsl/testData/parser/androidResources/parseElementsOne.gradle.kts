android {
  androidResources {
    additionalParameters = listOf("abcd")
    cruncherEnabled = true
    cruncherProcesses = 1
    failOnMissingConfigEntry = false
    ignoreAssets = "efgh"
    noCompress("a")
  }
}
