androidApp {
  dependenciesDcl {
    implementation("com.example.libs:lib1:0.+")
    compile(files("lib1.jar"))
    //api(fileTree(mapOf("dir" to "libs", "include" to  listOf("*.jar"))))
  }
}