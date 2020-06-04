android {
  externalNativeBuild {
    ndkBuild {
      setVersion("1.2.3")
      path = file("foo/bar/file.txt")
    }
  }
}