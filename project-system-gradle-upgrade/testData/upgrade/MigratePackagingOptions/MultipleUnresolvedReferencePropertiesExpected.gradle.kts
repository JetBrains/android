val abc by extra("a" + "bc")
val def by extra("d" + "ef")
val fooSo by extra("foo" + ".so")
val foo by extra("f" + "oo")
android {
  packagingOptions {
    jniLibs {
      keepDebugSymbols += setOf(barSo, bar2So)
      excludes += setOf(def, abcSo)
      pickFirsts += setOf(fooSo, foo)
    }
    resources {
      merges += setOf(abc, def)
      excludes += setOf(def, abcSo)
      pickFirsts += setOf(fooSo, foo)
    }
  }
}
