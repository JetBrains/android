kotlin {
  sourceSets {
    create("set") {
      dependencies {
        implementation("com.example:bar:1.0")
      }
    }
  }
}