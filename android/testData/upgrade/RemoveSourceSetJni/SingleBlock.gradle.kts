android {
  compileSdkVersion(30)
  sourceSets {
    getByName("main") {
      jni {
        srcDir "jniSource"
      }
    }
  }
}