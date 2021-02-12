android {
  packagingOptions {
    resources {
      excludes += setOf("foo")
      pickFirsts += setOf("bar", "baz")
      merges += setOf("a", "b", "c")
    }
  }
}
