android {
  defaultConfig {
    setApplicationId("com.example.myapplication-1")
    setDimension("efgh")
    maxSdkVersion(24)
    minSdkVersion("android-C")
    setMultiDexEnabled(false)
    targetSdkVersion("android-J")
    setTestApplicationId("com.example.myapplication-1.test")
    setTestFunctionalTest(true)
    setTestHandleProfiling(false)
    testInstrumentationRunner("efgh")
    useJack(true)
    setVersionCode(2)
    setVersionName("2.0")
  }
}
