android {
  testOptions {
    reportDir = "reportDirectory"
    resultsDir = "resultsDirectory"
    unitTests.isReturnDefaultValues = true
    execution = "ANDROID_TEST_ORCHESTRATOR"
    failureRetention {
      enable = true
      maxSnapshots = 3
    }
    emulatorSnapshots {
      compressSnapshots = false
      enableForTestFailures = true
      maxSnapshotsForTestFailures = 4
    }
  }
}
