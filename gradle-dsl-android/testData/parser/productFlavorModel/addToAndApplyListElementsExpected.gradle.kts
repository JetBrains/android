android {
  defaultConfig {
    consumerProguardFiles("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt")
    proguardFiles("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt")
    resourceConfigurations += setOf("abcd", "efgh", "xyz")
    resValue("abcd", "efgh", "ijkl")
    resValue("mnop", "qrst", "uvwx")
  }
}
