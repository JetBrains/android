android {
  defaultConfig {
    consumerProguardFiles("proguard-android.txt", "proguard-rules.pro")
    setProguardFiles(listOf("proguard-android.txt", "proguard-rules.pro"))
    resConfigs("abcd", "efgh")
    resValue("abcd", "efgh", "ijkl")
    resValue("mnop", "qrst", "uvwx")
  }
}
