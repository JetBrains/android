android {
  packagingOptions {
    resources {
      excludes += listOf("foo")
      pickFirsts += listOf("bar", "baz")
      merges += listOf("a", "b", "c")
    }
  }
}
