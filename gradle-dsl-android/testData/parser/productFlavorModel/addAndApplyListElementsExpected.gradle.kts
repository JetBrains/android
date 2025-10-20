android {
  defaultConfig {
    consumerProguardFiles("proguard-android.txt")
    proguardFiles("proguard-android.txt", "proguard-rules.pro")
    resourceConfigurations += setOf("abcd")
    resValue("mnop", "qrst", "uvwx")
  }
}
