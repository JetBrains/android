kotlin {
  sourceSets {
    getByName("commonMain") {
      dependencies {
        implementation("org.junit:junit:4.11")
      }
    }
    getByName("commonTest") {
      dependencies {
        implementation("org.junit:junit:4.11")
      }
    }
  }
}