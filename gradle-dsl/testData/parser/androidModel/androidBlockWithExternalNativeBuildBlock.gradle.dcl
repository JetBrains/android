androidApp {
  externalNativeBuild {
    cmake {
      path = file("foo/bar")
      version = "1.2.3"
    }
  }
}