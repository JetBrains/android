val dep by extra("com.example:foo:1.2.3")

dependencies {
  api(dep) {
    exclude(mapOf("group" to "a", "module" to "b"))
  }
  compile("junit:junit:4.12")
}