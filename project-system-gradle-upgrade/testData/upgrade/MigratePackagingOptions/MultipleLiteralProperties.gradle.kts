android {
  packagingOptions {
    merge("abc")
    merge("def")
    pickFirst("foo.so")
    pickFirst("foo")
    doNotStrip("bar.so")
    doNotStrip("bar2.so")
    exclude("def")
    exclude("abc.so")
  }
}
