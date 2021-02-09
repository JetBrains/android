android {
  packagingOptions {
    jniLibs {
      useLegacyPackaging = true
      excludes += listOf("foo")
      pickFirsts += listOf("bar", "baz")
      keepDebugSymbols += listOf("a", "b", "c")
    }
  }
}
