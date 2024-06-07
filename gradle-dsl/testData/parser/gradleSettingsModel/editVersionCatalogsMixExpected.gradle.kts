dependencyResolutionManagement {
  versionCatalogs {
    create("foo") {
      from(files("gradle/foo.versions.toml"))
    }
    create("bar") {
      from("org.example:bar:1.0")
    }
  }
}
