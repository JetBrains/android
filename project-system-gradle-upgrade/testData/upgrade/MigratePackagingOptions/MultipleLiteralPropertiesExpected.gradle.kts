android {
  packagingOptions {
    jniLibs {
      keepDebugSymbols += setOf("bar.so", "bar2.so")
      excludes += setOf("abc.so")
      pickFirsts += setOf("foo.so")
    }
    resources {
      merges += setOf("abc", "def")
      excludes += setOf("def")
      pickFirsts += setOf("foo")
    }
  }
}
