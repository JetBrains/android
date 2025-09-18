android {
  testOptions {
    emulatorSnapshots {
      enableForTestFailures = true
      maxSnapshotsForTestFailures = 100
    }
    failureRetention {
      enable = true
      maxSnapshots = 100
    }
    animationsDisabled = true  // This is unrelated property and should not be removed.
  }
}
