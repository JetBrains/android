val fancyF by extra(false)
val fancyT by extra(true)

android {
  buildTypes {
    getByName("release") {
      isTestCoverageEnabled = fancyF
    }
    getByName("debug") {
      isTestCoverageEnabled = fancyT
    }
    create("otherF") {
      isTestCoverageEnabled = fancyF
    }
    create("otherT") {
      isTestCoverageEnabled = fancyT
    }
    create("otherMissing ") {

    }
  }
}
