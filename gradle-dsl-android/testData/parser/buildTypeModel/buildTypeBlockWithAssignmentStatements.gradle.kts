android {
  buildTypes {
    create("xyz") {
      applicationIdSuffix = "mySuffix"
      consumerProguardFiles("proguard-android.txt", "proguard-rules.pro")
      isCrunchPngs = true
      isDebuggable = true
      isDefault = true
      isEmbedMicroApp = true
      isJniDebuggable= true
      manifestPlaceholders = mutableMapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2")
      isMinifyEnabled = true
      multiDexEnabled = true
      proguardFiles(listOf("proguard-android.txt", "proguard-rules.pro"))
      isPseudoLocalesEnabled = true
      isRenderscriptDebuggable = true
      renderscriptOptimLevel = 1
      isShrinkResources = true
      isTestCoverageEnabled = true
      useJack = true
      isUseProguard = true
      versionNameSuffix = "abc"
      isZipAlignEnabled = true
      enableUnitTestCoverage = true
      enableAndroidTestCoverage = true
    }
  }
}
