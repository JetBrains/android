# TODO

## Android Studio
  - FDR does not detect changes if you happen to compile in between deploys
  - FDR gradle invocation relies on getting timestamps for .arsc before and after.
    This means that we can't rely on the regular gradle invocation, and have to let
    the FDR performUpdate call Gradle.
  - Current infrastructure works only for a single device, run supports multiple devices
  - Currently we exit early in AndroidRunningState if we do a fast deploy.
    This means that debug action won't work.
  - If Gradle build for fast deploy fails, FDR detects that there are no changes.
  - logcat spam from fd (shows up in the app's logcat)
  - run config mechanism: run action works, but not debug
  - need a stop button to force terminate FDR?
  - When the app is running in the background, instant run detects no changes, but
    doesn't bring it to the foreground..

# Gradle
  - incrementalDex fails if a closure is removed from a class, e.g. if you comment out
    a line like `view.setOnClickListener(new Listener() {} )`, gradle will fail with
    the exception MainActivity$1.class is not a file