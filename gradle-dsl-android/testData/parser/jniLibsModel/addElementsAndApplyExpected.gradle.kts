android {
  packagingOptions {
    jniLibs {
      excludes += setOf("excludes1", "excludes2")
      pickFirsts += setOf("pickFirsts1", "pickFirsts2")
      keepDebugSymbols += setOf("keepDebugSymbols1")
    }
  }
}
