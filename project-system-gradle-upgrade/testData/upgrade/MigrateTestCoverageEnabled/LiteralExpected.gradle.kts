android {
  buildTypes {
    getByName("release") {
      enableUnitTestCoverage = false
      enableAndroidTestCoverage = false
    }
    getByName("debug") {
      enableUnitTestCoverage = true
      enableAndroidTestCoverage = true
    }
    create("otherF") {
      enableUnitTestCoverage = false
      enableAndroidTestCoverage = false
    }
    create("otherT") {
      enableUnitTestCoverage = true
      enableAndroidTestCoverage = true
    }
    create("otherMissing ") {

    }
  }
}
