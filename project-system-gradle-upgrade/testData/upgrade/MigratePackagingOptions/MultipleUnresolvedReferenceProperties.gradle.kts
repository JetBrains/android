val abc by extra("a" + "bc")
val def by extra("d" + "ef")
val fooSo by extra("foo" + ".so")
val foo by extra("f" + "oo")
android {
  packagingOptions {
    merge(abc)
    merge(def)
    pickFirst(fooSo)
    pickFirst(foo)
    doNotStrip(barSo)
    doNotStrip(bar2So)
    exclude(def)
    exclude(abcSo)
  }
}
