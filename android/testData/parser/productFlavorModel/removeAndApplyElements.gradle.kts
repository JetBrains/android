android {
  defaultConfig {
    applicationId("com.example.myapplication")
    consumerProguardFiles("proguard-android.txt", "proguard-rules.pro")
    setDimension("abcd")
    manifestPlaceholders(mapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2"))
    maxSdkVersion(23)
    minSdkVersion(15)
    multiDexEnabled(true)
    proguardFiles("proguard-android.txt", "proguard-rules.pro")
    resConfigs("abcd", "efgh")
    resValue("abcd", "efgh", "ijkl")
    targetSdkVersion(22)
    testApplicationId("com.example.myapplication.test")
    setTestFunctionalTest(false)
    setTestHandleProfiling(true)
    testInstrumentationRunner("abcd")
    testInstrumentationRunnerArguments(mapOf("size" to "medium","foo" to "bar"))
    useJack(false)
    versionCode(1)
    versionName("1.0")
  }
}
