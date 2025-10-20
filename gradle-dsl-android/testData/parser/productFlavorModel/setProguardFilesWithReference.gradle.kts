val list by extra(listOf(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"))
android {
  defaultConfig {
    setProguardFiles(list)
  }
}
