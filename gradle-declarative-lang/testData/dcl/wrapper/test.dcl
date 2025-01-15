javaApplication {
  javaVersion = 21
  mainClass = "com.example.App"

  dependencies {
    implementation(project(":java-util"))
    implementation("com.google.guava:guava:32.1.3-jre")
  }
}