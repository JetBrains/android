## Performance tools manual tests

These tests require a device connected. On emulator it's enough to just test basic monitoring.
Marked **bold** are test expectations.

### Test basic monitoring is enabled

Import the sample "Displaying Bitmaps". Make sure the gradle-plugin in use is at least 2.4.

1. Make sure that the current Run Configuration has "Advance Profiling" disabled.
2. Launch the app on a device with API >= 21.
3. Select "Android Profiler" tool window in Android Studio.
4. Observe the **monitors streaming**, rendering graphs of underlying data.
5. Interact with the app.
6. Observe the **CPU monitor changing**.
7. Click on an image and see that the data in the **memory monitor changes**.
8. Go back to the main screen menu and clear the cache (via the top-right "..." menu button).
9. Scroll the app down a page or two, make sure the images start loading in on the device, and observe that the **network monitor changes**. There should be indications that data is being both sent and received.

### Advance monitoring

1. Go to the run configuration.
2. Enable "Advance Profiling" in the Profiler tab.
3. Relaunch the app.
4. Observe that **the current activity shows up in the event monitor** (top area).
5. Touch and drag the app, rotate the phone, press volume and back buttons. **Observe those events showing up in the monitor**.

### CPU profiling sanity check

1. Click on the CPU monitor.
2. With "Sampled" selected, start recording and wait 5 seconds.
3. Stop recording and **observe the trace being rendered on screen**.
4. Click the back arrow to go back to the monitors.

### Memory checks

1. Click the Memory Monitor
2. Click on the heap dump icon and observe the **heap dump taking place and displaying**.
3. Click on "Record" to start allocation tracking and wait 5 seconds.
4. Click again to stop recording and observe **the allocations being displayed on screen**.
4. Click the back arrow go to back to the monitors.

### Network checks

1. Click the Network Monitor
2. Interact with the app until there are further network requests.
3. Select a range in the timeline that contains network activity.
4. Observe the table **populated with all the network requests**.
5. Click on any row in the table.
6. Observe that **a new window opens up showing details about the contents of the request**.

