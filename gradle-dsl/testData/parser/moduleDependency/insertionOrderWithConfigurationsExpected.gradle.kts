dependencies {
  api(project(mapOf("path" to ":module1", "configuration" to "api")))
  implementation("androidx.constraintlayout:constraintlayout:1.1.3")
  testImplementation(project(mapOf("path" to ":module2", "configuration" to "implementation")))
  androidTestApi(project(mapOf("path" to ":module3", "configuration" to "api")))
}
