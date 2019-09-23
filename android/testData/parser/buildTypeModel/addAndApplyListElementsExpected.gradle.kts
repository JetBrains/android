android {
  buildTypes {
    create("xyz") {
      buildConfigField("abcd", "efgh", "ijkl")
      consumerProguardFiles = listOf("proguard-android.txt")
      proguardFiles("proguard-android.txt")
      resValue("mnop", "qrst", "uvwx")

    }
  }
}
