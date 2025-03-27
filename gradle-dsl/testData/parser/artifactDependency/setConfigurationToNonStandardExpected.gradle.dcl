androidApp {
  buildTypes {
    buildType("custom") {
    }
  }
  dependenciesDcl {
    customImplementation("com.example:artifact:1.0")
  }
}
