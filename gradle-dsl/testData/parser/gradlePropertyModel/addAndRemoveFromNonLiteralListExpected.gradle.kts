android {
  defaultConfig {
    proguardFiles(
      "z.txt",
      getDefaultProguardFile("proguard-android-optimize.txt"),
      "proguard-rules.txt",
      "proguard-rules2.txt"
    )
  }
}
