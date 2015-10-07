# TODO

## Android Studio
  - Current infrastructure works only for a single device, run supports multiple devices
  - Currently we exit early in AndroidRunningState if we do a fast deploy.
    This means that debug action won't work.
  - If Gradle build for fast deploy fails, FDR detects that there are no changes.
  - logcat spam from fd (shows up in the app's logcat)
  - need a stop button to force terminate FDR?
  - When the app is running in the background, instant run detects no changes, but
    doesn't bring it to the foreground..

# Gradle
  - incrementalDex fails if a closure is removed from a class, e.g. if you comment out
    a line like `view.setOnClickListener(new Listener() {} )`, gradle will fail with
    the exception MainActivity$1.class is not a file