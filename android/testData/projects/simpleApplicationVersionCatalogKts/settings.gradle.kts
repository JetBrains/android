pluginManagement {
  repositories {
    // This will be populated by AndroidGradleTestCase
  }
}
dependencyResolutionManagement {
  repositories {
    // This will be populated by AndroidGradleTestCase
  }
  versionCatalogs {
    create("libsTest") {
      from(files("./gradle/libsTest.versions.toml"))
    }
  }
}

include(":app")