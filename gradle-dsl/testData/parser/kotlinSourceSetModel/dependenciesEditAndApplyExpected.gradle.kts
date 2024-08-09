kotlin {
  sourceSets {
    create("set") {
      dependencies {
        implementation("com.example:bar:2.0")
      }
    }
  }
}