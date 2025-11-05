android {
  buildTypes {
    getByName("release") {
      buildTypeNested {
        nestedVal("some")
      }
    }
  }
}