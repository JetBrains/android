android {
  buildTypes {
    create("xyz") {
      applicationIdSuffix = "mySuffix"
      setConsumerProguardFiles(listOf("proguard-android.txt", "proguard-rules.pro"))
      isCrunchPngs = false
      isDebuggable = true
      isDefault = false
      isEmbedMicroApp = false
      isJniDebuggable = true
      manifestPlaceholders = mutableMapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2")
      isMinifyEnabled = false
      multiDexEnabled = true
      proguardFiles("proguard-android.txt", "proguard-rules.pro")
      isPseudoLocalesEnabled = false
      isRenderscriptDebuggable = true
      renderscriptOptimLevel = 1
      isShrinkResources = false
      isTestCoverageEnabled = true
      //useJack(false)
      isUseProguard = false
      versionNameSuffix = "abc"
      isZipAlignEnabled = true
      enableUnitTestCoverage = true
      enableAndroidTestCoverage = true
    }
  }
}
android.buildTypes.getByName("xyz") {
  applicationIdSuffix = "mySuffix-1"
  setConsumerProguardFiles(listOf("proguard-android-1.txt", "proguard-rules-1.pro"))
  isCrunchPngs = true
  isDebuggable = false
  isDefault = true
  isEmbedMicroApp = true
  isJniDebuggable = false
  manifestPlaceholders = mutableMapOf("activityLabel3" to "defaultName3", "activityLabel4" to "defaultName4")
  isMinifyEnabled = true
  multiDexEnabled = false
  setProguardFiles(listOf("proguard-android-1.txt", "proguard-rules-1.pro"))
  isPseudoLocalesEnabled = true
  isRenderscriptDebuggable = false
  renderscriptOptimLevel = 2
  isShrinkResources = true
  isTestCoverageEnabled = false
  //useJack(true)
  isUseProguard = true
  versionNameSuffix = "abc-1"
  isZipAlignEnabled = false
  enableUnitTestCoverage = false
  enableAndroidTestCoverage = false
}
android.buildTypes.getByName("xyz").applicationIdSuffix = "mySuffix-3"
