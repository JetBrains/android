android {
  packagingOptions {
    resources {
      excludes += setOf("excludes1", "excludes2")
      pickFirsts += setOf("pickFirsts1", "pickFirsts2")
      merges += setOf("merges1")
    }
  }
}
