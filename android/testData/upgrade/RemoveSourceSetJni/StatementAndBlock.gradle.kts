android {
  compileSdkVersion 30
  sourceSets.main.jni.exclude("jniSource/foo")
  sourceSets {
    main {
      jni {
        srcDir("jniSource")
      }
    }
  }
}
