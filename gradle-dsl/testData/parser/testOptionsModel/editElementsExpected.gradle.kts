android {
  testOptions {
    reportDir = "otherReportDir"
    resultsDir = "otherResultsDir"
    unitTests.isReturnDefaultValues = false
    execution = "ANDROID_TEST_ORCHESTRATOR"
    failureRetention {
      enable = false
      maxSnapshots = 4
    }
    emulatorSnapshots {
      compressSnapshots = true
      enableForTestFailures = false
      maxSnapshotsForTestFailures = 3
    }
  }
}
