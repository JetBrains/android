android {
  packagingOptions {
    resources {
      excludes += "excludesX"
      pickFirsts += listOf("pickFirstsX")
      merges += listOf("mergesX", "merges2")
    }
  }
}
