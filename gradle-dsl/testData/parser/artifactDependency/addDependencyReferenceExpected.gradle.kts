val dep by extra("com.example:foo:1.2.3")

dependencies {
  api(dep)
  compile("junit:junit:4.12")
}