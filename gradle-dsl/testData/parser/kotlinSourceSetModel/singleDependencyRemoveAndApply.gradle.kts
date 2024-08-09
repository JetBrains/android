kotlin {
  sourceSets {
    create("set") {
      dependencies {
        implementation("com.example:bar:1.0")
        implementation("com.example:bar:2.0")
      }
    }
  }
}