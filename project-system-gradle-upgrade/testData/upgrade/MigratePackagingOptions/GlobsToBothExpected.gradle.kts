android {
  packagingOptions {
    jniLibs {
      excludes += setOf("baz.??", "quux.[a-z][a-z]")
      pickFirsts += setOf("foo.{so,jar}", "bar.*")
    }
    resources {
      excludes += setOf("baz.??", "quux.[a-z][a-z]")
      pickFirsts += setOf("foo.{so,jar}", "bar.*")
    }
  }
}