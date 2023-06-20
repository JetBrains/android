dependencyResolutionManagement {
  versionCatalogs {
    create("foo") {
      from(files("gradle/foo.versions.toml"))
    }
    create("bar") {
    }
  }
}
