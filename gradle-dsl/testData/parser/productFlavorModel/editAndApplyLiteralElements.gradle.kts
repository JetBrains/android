android {
  defaultConfig {
    setApplicationId("com.example.myapplication")
    setDimension("abcd")
    maxSdkVersion(23)
    minSdkVersion("android-B")
    setMultiDexEnabled(true)
    targetSdkVersion("android-I")
    setTestApplicationId("com.example.myapplication.test")
    setTestFunctionalTest(false)
    setTestHandleProfiling(true)
    testInstrumentationRunner("abcd")
    useJack(false)
    setVersionCode(1)
    setVersionName("1.0")
  }
}
