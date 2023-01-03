dependencies {
  testCompile(project(":abc"))
  testCompile(project(mapOf("path" to ":xyz"))))
  compile(project(":klm"))
  compile(project(":"))
  compile(project(mapOf("path" to ":pqr", "configuration" to "config")))
}