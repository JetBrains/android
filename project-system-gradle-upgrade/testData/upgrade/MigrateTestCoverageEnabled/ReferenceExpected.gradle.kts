val fancyF by extra(false)
val fancyT by extra(true)

android {
  buildTypes {
    getByName("release") {
      enableUnitTestCoverage = fancyF
      enableAndroidTestCoverage = fancyF
    }
    getByName("debug") {
      enableUnitTestCoverage = fancyT
      enableAndroidTestCoverage = fancyT
    }
    create("otherF") {
      enableUnitTestCoverage = fancyF
      enableAndroidTestCoverage = fancyF
    }
    create("otherT") {
      enableUnitTestCoverage = fancyT
      enableAndroidTestCoverage = fancyT
    }
    create("otherMissing ") {

    }
  }
}
