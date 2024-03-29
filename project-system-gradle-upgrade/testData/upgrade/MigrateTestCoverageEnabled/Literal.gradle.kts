android {
  buildTypes {
    getByName("release") {
      isTestCoverageEnabled = false
    }
    getByName("debug") {
      isTestCoverageEnabled = true
    }
    create("otherF") {
      isTestCoverageEnabled = false
    }
    create("otherT") {
      isTestCoverageEnabled = true
    }
    create("otherMissing ") {

    }
  }
}
