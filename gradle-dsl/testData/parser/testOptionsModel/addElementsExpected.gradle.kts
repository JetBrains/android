android {
  testOptions {
    reportDir = "reportDirectory"
    resultsDir = "resultsDirectory"
    execution = "ANDROID_TEST_ORCHESTRATOR"
    unitTests {
      isReturnDefaultValues = true
    }
    failureRetention {
      enable = true
      maxSnapshots = 3
    }
  }
}
