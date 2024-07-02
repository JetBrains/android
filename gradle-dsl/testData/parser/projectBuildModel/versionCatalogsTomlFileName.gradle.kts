dependencyResolutionManagement {
  versionCatalogs {
    create("libs") {
      from(files("gradle/libs.toml"))
    }
    create("foo") {
      from(files("gradle/foo.toml"))
    }
  }
}
