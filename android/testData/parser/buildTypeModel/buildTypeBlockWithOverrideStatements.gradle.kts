android {
  buildTypes {
    create("xyz") {
      applicationIdSuffix = "mySuffix"
      consumerProguardFiles("proguard-android.txt", "proguard-rules.pro")
      isDebuggable = true
      isEmbedMicroApp = false
      isJniDebuggable = true
      manifestPlaceholders = mapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2")
      isMinifyEnabled = false
      multiDexEnabled = true
      proguardFiles("proguard-android.txt", "proguard-rules.pro")
      isPseudoLocalesEnabled = false
      isRenderscriptDebuggable = true
      renderscriptOptimLevel = 1
      isShrinkResources = false
      isTestCoverageEnabled = true
      //useJack(false)
      versionNameSuffix = "abc"
      isZipAlignEnabled = true
    }
  }
}
android.buildTypes.getByName("xyz") {
  applicationIdSuffix = "mySuffix-1"
  consumerProguardFiles("proguard-android-1.txt", "proguard-rules-1.pro")
  isDebuggable = false
  isEmbedMicroApp = true
  isJniDebuggable = false
  manifestPlaceholders = mapOf("activityLabel3" to "defaultName3", "activityLabel4" to "defaultName4")
  isMinifyEnabled = true
  multiDexEnabled = false
  // TODO(xof): in the Kotlin DSL, we can't assign to proguardFiles;
  // this test wants to override the current property (rather than
  // extend), which is in principle possible with setProguardFiles
  // but currently unsupported.
  // proguardFiles("proguard-android-1.txt", "proguard-rules-1.pro")
  isPseudoLocalesEnabled = true
  isRenderscriptDebuggable = false
  renderscriptOptimLevel = 2
  isShrinkResources = true
  isTestCoverageEnabled = false
  //useJack(true)
  versionNameSuffix = "abc-1"
  isZipAlignEnabled = false
}
android.buildTypes.getByName("xyz").applicationIdSuffix = "mySuffix-3"
