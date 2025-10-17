android {
  testOptions {
    suites {
      create("testSuite") {
        targetVariants += listOf("debug")
        targets {
          create("default") {
          }
        }
        assets {
        }
        useJunitEngine {
          inputs += listOf(com.android.build.api.dsl.AgpTestSuiteInputParameters.TESTED_APKS)
          includeEngines += listOf("test-engine-id")
          enginesDependencies("org.junit.platform:junit-platform-launcher")
          enginesDependencies("org.junit.platform:junit-platform-engine:1.12.0")
          enginesDependencies(libs.junit)
        }
      }
    }
  }
}
