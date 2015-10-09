# TODO

## Android Studio
  - Patch runner config needs to be updated to support incremental launch for debug
  - Incremental update for tests doesn't work since the test process isn't actually running once tests are complete
  - need a stop button to force terminate FDR?
  - If Gradle build for fast deploy fails, FDR detects that there are no changes.
  - logcat spam from fd (shows up in the app's logcat)
  - When the app is running in the background, instant run detects no changes, but
    doesn't bring it to the foreground..

# Gradle
  - incrementalDex fails if a closure is removed from a class, e.g. if you comment out
    a line like `view.setOnClickListener(new Listener() {} )`, gradle will fail with
    the exception MainActivity$1.class is not a file