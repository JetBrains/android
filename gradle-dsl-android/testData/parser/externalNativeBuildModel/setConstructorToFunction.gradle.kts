android {
  externalNativeBuild {
    ndkBuild {
      setPath(File("foo", "Android.mk"))
      setVersion("1.2.3")
    }
  }
}