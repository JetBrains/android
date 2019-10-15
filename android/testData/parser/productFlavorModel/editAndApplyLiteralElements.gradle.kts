android {
  defaultConfig {
    applicationId("com.example.myapplication")
    setDimension("abcd")
    maxSdkVersion(23)
    minSdkVersion("15")
    multiDexEnabled(true)
    targetSdkVersion("22")
    testApplicationId("com.example.myapplication.test")
    setTestFunctionalTest(false)
    setTestHandleProfiling(true)
    testInstrumentationRunner("abcd")
    useJack(false)
    versionCode(1)
    versionName("1.0")
  }
}
