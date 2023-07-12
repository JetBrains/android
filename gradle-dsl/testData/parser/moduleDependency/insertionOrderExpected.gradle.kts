dependencies {
  api(project(":module1"))
  implementation("androidx.constraintlayout:constraintlayout:1.1.3")
  testImplementation(project(":module2"))
  androidTestApi(project(":module3"))
}
