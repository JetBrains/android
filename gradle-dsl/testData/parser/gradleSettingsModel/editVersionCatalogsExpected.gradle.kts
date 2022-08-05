dependencyResolutionManagement {
  versionCatalogs {
    getByName("libs") {
      from(files("gradle/new-libs.versions.toml"))
    }
    create("foo") {
    }
    create("bar") {
      from(files("gradle/bar.versions.toml"))
    }
  }
}
