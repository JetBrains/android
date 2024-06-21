kotlin {
  sourceSets {
    create("set") {
      dependencies {
        api("org.junit:junit:4.11")
        implementation("com.example:bar:1.0")
      }
    }
  }
}