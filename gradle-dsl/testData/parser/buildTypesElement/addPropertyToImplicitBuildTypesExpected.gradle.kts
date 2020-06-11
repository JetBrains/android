android {
  buildTypes {
    getByName("release") {
      applicationIdSuffix = "-release"
    }
    getByName("debug") {
      applicationIdSuffix = "-debug"
    }
  }
}
