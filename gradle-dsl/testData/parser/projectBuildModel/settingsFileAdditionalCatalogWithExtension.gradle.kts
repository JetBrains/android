dependencyResolutionManagement {
  defaultLibrariesExtensionName = "dep"
  versionCatalogs {
    testLibs {
      from(files("gradle/testLibs.versions.toml"))
    }
  }
}