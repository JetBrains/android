val abc by extra("abc")
val def by extra("def")
val fooSo by extra("foo.so")
val foo by extra("foo")
val barSo by extra("bar.so")
val bar2So by extra("bar2.so")
val abcSo by extra("abc.so")
android {
  packagingOptions {
    jniLibs {
      keepDebugSymbols += setOf(barSo, bar2So)
      excludes += setOf(abcSo)
      pickFirsts += setOf(fooSo)
    }
    resources {
      merges += setOf(abc, def)
      excludes += setOf(def)
      pickFirsts += setOf(foo)
    }
  }
}
