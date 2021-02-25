android {
  packagingOptions {
    jniLibs {
      useLegacyPackaging = true
      excludes += setOf("foo")
      pickFirsts += setOf("bar", "baz")
      keepDebugSymbols += setOf("a", "b", "c")
    }
  }
}
