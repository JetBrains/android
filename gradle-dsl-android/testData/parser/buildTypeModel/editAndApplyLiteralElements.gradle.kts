android {
  buildTypes {
    create("xyz") {
      applicationIdSuffix = "mySuffix"
      isCrunchPngs = true
      isDebuggable = true
      isDefault = true
      isEmbedMicroApp = false
      isJniDebuggable = true
      isMinifyEnabled = false
      multiDexEnabled = true
      isPseudoLocalesEnabled = false
      isRenderscriptDebuggable = true
      renderscriptOptimLevel = 1
      isShrinkResources = false
      isTestCoverageEnabled = true
      useJack = false
      isUseProguard = true
      versionNameSuffix = "abc"
      isZipAlignEnabled = true
      enableUnitTestCoverage = true
      enableAndroidTestCoverage = true
    }
  }
}
