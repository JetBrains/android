android {
  defaultConfig {
    consumerProguardFiles("proguard-android.txt")
    proguardFiles("proguard-android.txt")
    resourceConfigurations += setOf("abcd")
    resValue("abcd", "efgh", "ijkl")
  }
}
