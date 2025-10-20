android {
  defaultConfig {
    applicationId = "com.example.myapplication"
    dimension = "abcd"
    maxSdkVersion = 23
    minSdkVersion("android-B")
    multiDexEnabled = true
    targetSdkVersion("android-I")
    testApplicationId = "com.example.myapplication.test"
    testFunctionalTest = false
    testHandleProfiling = true
    testInstrumentationRunner = "abcd"
    useJack = false
    versionCode = 1
    versionName = "1.0"
  }
}