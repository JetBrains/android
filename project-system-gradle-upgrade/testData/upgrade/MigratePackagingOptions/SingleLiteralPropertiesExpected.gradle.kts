android {
  packagingOptions {
    jniLibs {
      keepDebugSymbols += setOf("bar.so")
      pickFirsts += setOf("foo.so")
    }
    resources {
      merges += setOf("abc")
      excludes += setOf("def")
    }
  }
}
