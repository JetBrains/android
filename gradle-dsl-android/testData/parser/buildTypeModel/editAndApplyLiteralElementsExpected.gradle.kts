android {
  buildTypes {
    create("xyz") {
      applicationIdSuffix = "mySuffix-1"
      isCrunchPngs = false
      isDebuggable = false
      isDefault = false
      isEmbedMicroApp = true
      isJniDebuggable = false
      isMinifyEnabled = true
      multiDexEnabled = false
      isPseudoLocalesEnabled = true
      isRenderscriptDebuggable = false
      renderscriptOptimLevel = 2
      isShrinkResources = true
      isTestCoverageEnabled = false
      useJack = true
      isUseProguard = false
      versionNameSuffix = "def"
      isZipAlignEnabled = false
      enableUnitTestCoverage = false
      enableAndroidTestCoverage = false
    }
  }
}
