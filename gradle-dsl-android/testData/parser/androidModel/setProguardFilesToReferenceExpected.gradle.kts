val foo by extra("foo")
val bar by extra("bar")
val baz by extra("baz")
val quux by extra("quux")
val foobar by extra(listOf("foo", "bar"))
val bazquux by extra(listOf("baz", "quux"))
android {
  defaultConfig {
    setConsumerProguardFiles(bazquux)
    setProguardFiles(foobar)
  }
}
