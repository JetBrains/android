android {
  packagingOptions {
    resources {
      excludes += "excludes1"
      pickFirsts += listOf("pickFirsts1")
      merges += listOf()
    }
  }
}
