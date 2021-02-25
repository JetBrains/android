android {
  packagingOptions {
    jniLibs {
      excludes += "excludes1"
      pickFirsts += listOf("pickFirsts1")
      keepDebugSymbols += listOf()
    }
  }
}
