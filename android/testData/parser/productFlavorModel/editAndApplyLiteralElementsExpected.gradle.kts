android {
  defaultConfig {
    applicationId("com.example.myapplication-1")
    setDimension("efgh")
    maxSdkVersion(24)
    minSdkVersion("16")
    multiDexEnabled(false)
    targetSdkVersion("23")
    testApplicationId("com.example.myapplication-1.test")
    setTestFunctionalTest(true)
    setTestHandleProfiling(false)
    testInstrumentationRunner("efgh")
    useJack(true)
    versionCode("2")
    versionName("2.0")
  }
}
