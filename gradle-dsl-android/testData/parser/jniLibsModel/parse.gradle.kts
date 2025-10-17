android {
  packagingOptions {
    jniLibs {
      useLegacyPackaging = true
      excludes += "foo"
      pickFirsts += listOf("bar", "baz")
      keepDebugSymbols += "a"
      keepDebugSymbols += "b"
      keepDebugSymbols += "c"
    }
  }
}
