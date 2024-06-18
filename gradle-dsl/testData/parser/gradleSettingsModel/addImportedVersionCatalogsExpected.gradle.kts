dependencyResolutionManagement {
  versionCatalogs {
    create("foo") {
      from("com.mycompany:catalog:1.0")
    }
    create("bar") {
    }
  }
}
