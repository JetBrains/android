android {
  packagingOptions {
    doNotStrip = setOf("doNotStrip1", "doNotStrip2")
    doNotStrip("doNotStrip3")
    excludes = setOf("exclude1", "exclude2")
    exclude("exclude3")
    merges = setOf("merge1", "merge2")
    merge("merge3")
    pickFirsts = setOf("pickFirst1", "pickFirst2")
    pickFirst("pickFirst3")
  }
}
