dependencyResolutionManagement {
  versionCatalogs {
    create("testLibs") {
      from(files("gradle/testLibs.versions.toml"))
    }
  }
}