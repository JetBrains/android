android {
  buildTypes {
    create("foo") {
      isMinifyEnabled = true
    }
    create("bar") {
      initWith(buildTypes.getByName("foo"))
    }
  }
}
