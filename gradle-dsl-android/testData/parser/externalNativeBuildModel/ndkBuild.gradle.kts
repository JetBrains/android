android {
  externalNativeBuild {
    ndkBuild {
      path = file("foo/Android.mk")
      version = "1.2.3"
    }
  }
}
