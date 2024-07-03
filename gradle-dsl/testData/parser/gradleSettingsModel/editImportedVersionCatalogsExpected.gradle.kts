dependencyResolutionManagement {
  versionCatalogs {
    getByName("libs") {
      from("com.mycompany:new-catalog:1.0")
    }
    create("foo") {
    }
    create("bar") {
      from("org.example:foo:1.0")
    }
  }
}
