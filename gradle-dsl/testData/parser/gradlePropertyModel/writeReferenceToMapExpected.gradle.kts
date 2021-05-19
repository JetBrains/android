val mP by extra(mapOf("a" to "b", "c" to "d"))

android {
  compileSdkVersion(30)
  defaultConfig {
    setManifestPlaceholders(mP)
    targetSdkVersion(30)
  }
}
