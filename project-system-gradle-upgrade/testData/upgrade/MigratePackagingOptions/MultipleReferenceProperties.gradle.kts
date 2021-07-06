val abc by extra("abc")
val def by extra("def")
val fooSo by extra("foo.so")
val foo by extra("foo")
val barSo by extra("bar.so")
val bar2So by extra("bar2.so")
val abcSo by extra("abc.so")
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
