## Performance tools manual tests
##[WORK IN PROGRESS DO NOT TEST]


Run Android Studio with the new profilers enabled. To do that have in a local directory, for example:
`/tmp/profilers.vmoptions` with the following content

```
-Denable.experimental.profiling=true
```

And then open studio, for example on Mac, like this:

```
STUDIO_VM_OPTIONS=/tmp/profilers.vmoptions open Android\ Studio.app
```

These tests require a device connected.

### Global feature test

Import the sample "Displaying Bitmaps". Make sure the gradle-plugin in use is at least 2.4.

1. Launch the app on a device with API >= 21
2. Select "Android Profiler" tool window in Android Studio
3. Interact with the app
4. Observe monitors streaming memory, network, CPU and event information.


### CPU checks

TBD

### Memory checks

TBD

### Network checks

TBD

