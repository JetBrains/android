# Performance Tools Manual Tests (Using the QA App)

* These tests require a connected device.
* In order for your own experience to most closely match the screenshots used
throughout this document, it is recommended you use a quad core Android device.
* Sections marked **bold** below indicate *test expectations*.

[TOC]

## Launching the Application

If you have access to Android Studio source code (via `repo sync`), the most
reliable way to open the latest version of the project is to find it at
`$SRC/tools/base/profiler/integration-tests/ProfilerTester`.

Otherwise, try downloading it at
[res/perf-tools/ProfilerTester.zip](res/perf-tools/ProfilerTester.zip)

Once the project is opened:

1. Follow any prompts to install any missing dependencies (SDK, NDK, etc.)
1. Make sure that the current Run Configuration for "app" has
   "Advance Profiling" enabled.
   * `Run > Edit Configurations > app > Profiling tab > Enable Advanced Profiling`
1. Launch the app on a device with API >= 21 (i.e. Lollipop or newer)
1. Profiler behavior changes in O. Please test all scenarios twice,
   once with a pre-O (API <= 25) device and again with an O or newer (API > 25) device.
1. Select the "Android Profiler" tool window in Android Studio.
   * Alternately: `Tools > Android > Android Profiler`

## Scenarios

The app presents two pulldowns.

![Profiler QA app screenshot][app]

You use the first pulldown to chose a category (CPU, Memory, Network, Events,
etc.) and the second pulldown to prepare a particular scenario (Perodic Usage,
File Writing, etc.)

Once any scenario is selected, press the "run" button to test it. The scenario
will run for some amount of time (anywhere from a few seconds to a minute)
before stopping, at which point you can observe and confirm its effects.

For convenience, you can also press the "previous" and "next" buttons to
navigate through all scenarios in the order presented in this document.

## Profilers

By default, the "Android Profilers" toolbar presents a top-level view of all
profilers. From that view, you can click on any of the graphs to jump into a
more detailed view for that profiler. (*Note that Event profiling is an
exception, as it is always shown above all profilers*).

**Therefore, when running a scenario for a particular area, e.g. CPU, be sure
to open up that profiler in Android Studio to observe the scenario's detailed
effects.**

## CPU

### Periodic Usage

1. In the "Android Profiler" Toolbar, make sure you are on the CPU profiler.
1. In the QA App, select the "Periodic Usage" scenario.
1. Press the "run" button
1. You should see **CPU usage increase to around 70% for 2 seconds every 4
   seconds**
1. Look at the threads table and make sure you can see **three threads
   ("CpuAsyncTask #1", "CpuAsyncTask #2", "CpuAsyncTask #3")**
1. **Each thread should be responsible for its own CPU usage spikes.**
5. **Each thread should run for 2 seconds (green), sleep for 4 seconds (grey),
   and then close**.

![CPU - Periodic Usage][cpu-periodic]

### File Writing

1. In the "Android Profiler" Toolbar, make sure you are on the CPU profiler.
1. In the QA App, select the "File Writing" scenario.
1. Press the "run" button
1. You should see **four threads perform "writing" with running (green) and
   waiting state (light green)**
1. Wait 2 seconds after all writing threads stop running.
1. You should see **four threads reading the file.**

![CPU - File Writing][cpu-file-write]

### Native Code

**(This test is expected to work only on Android O+, API >= 26)**

1. In the "Android Profiler" Toolbar, make sure you are on the CPU profiler.
1. In the QA App, select the "Native Code" scenario.
1. In the CPU profiler, make sure the selected configuration is set to
   "Sampled (Native)" and press "Record"
1. In the QA App, press the "run" button.
1. After about five seconds, you should get a toast, showing the message
   "Finished calling native code"
1. In the CPU profiler, press "Stop" to finish the recording.
1. In the "THREADS" view, scroll down and select the thread with a name which
   will be something like "AsyncTask #3" or "AsyncTask #4" (the exact number may
   be different).
   * You'll know you found the right thread when you see a longer, solid, dark
     bar.
   because there should be a long, solid bar representing activity.
1. **In the "Call Chart" view, you should see both blue (system) and
   green (user) function calls.**
   * You can zoom in on the area and confirm that the green function call is
     called "DoExpensiveFpuCalculation".
   * *Optional:* You can also type "Fpu" into the method filter to highlight the
     native methods.

![CPU - Native Code][cpu-native]

---

![CPU - Native Code w/ Filter][cpu-native-filter]

### Startup Profiling

**(This test is expected to work only on Android O+, API >= 26)**

1. Edit the run configuration for "app", going to the "Profiling" tab.
1. Check the "Start recording a method trace on startup" option
   * Feel free to leave the particular trace config on "Sampled (Java)", but if
     you change it, just remember your choice for later.
1. Press "OK" and return to the main editor view.
1. Restart your profiling session by hitting the "profile" button.
1. In the "Androd Profiler" toolbar, you should observe it automatically open
   into the CPU profiler. A recording should already be in progress.
1. Stop the recording.
1. Look at the "Call Chart" view below.
1. **Verify you see "ZygoteInit.main" and "ActivityThread.main" in the list of
   methods.**
   * This confirms the profiler started recording before the first activity
     opened.
1. **Don't forget**: Go back into the run configuration for "app" and uncheck
   the "Start recording..." option.

![CPU - Startup Profiling Configuration][cpu-startup-config]
![CPU - Startup Profiling][cpu-startup]

### System Tracing

**(This test is expected to work only on Android O+, API >= 26)**

1. In the "Android Profiler" Toolbar, make sure you are on the CPU profiler.
1. In the QA App, select the "Code With Trace Markers" scenario.
1. In the CPU profiler, make sure the selected configuration is set to
   "System Trace" and press "Record"
1. In the QA App, press the "run" button.
1. After about five seconds, you should get a toast, showing the message
   "Computation finished"
1. In the CPU profiler, press "Stop" to finish the recording.
1. **Verify there's a "FRAMES" view (with "Main" and "Render" entries).**
1. **Verify there's a "KERNEL" view (with one or more "CPU" entries).**
1. In the "THREADS" view, scroll down and select the thread with a name which
   will be something like "AsyncTask #3" or "AsyncTask #4" (the exact number may
   be different).
   * You'll know you found the right thread when you see a longer, solid, grey
     bar. (It's grey because it makes calls to other threads and sleeps, waiting
     for them)
1. **In the "Trace Events" view, you should see a single dark green bar labelled
   "CodeWithTraceMarkersTask#execute"**

![CPU - Systrace][cpu-systrace]

### Automatic Recording

**(This test is expected to work only on Android O+, API >= 26)**

1. In the "Android Profiler" Toolbar, make sure you are on the CPU profiler.
1. In the QA App, select the "Automatic Recording" scenario.
1. In the QA App, press the "run" button.
1. After about five seconds, you should get a toast, showing the message
   "Computation finished"
1. **Note that a recording was automatically started and stopped in the CPU
   profiler without requiring explicitly pressing the "Record" button.**

![CPU - Automatic Recording][cpu-automatic]

### Export / Import Trace

1. In the "Android Profiler", after you've performed at least one CPU recording,
   you can find an option to export it to a file (a save icon present in the
   left hand sessions view).
1. Save the file anywhere you'd like, for example in "~/tmp" on Linux or Mac.
1. In the same sessions panel, press the "+" button. Select the file you've
   just saved.
1. When prompted which process you want to analyze, choose ".profilertester"
1. **Check that the CPU profiler is populated with the data you just exported.**

![CPU - Export Trace][cpu-trace-export]

### Trace Selection

1. In the "Android Profiler", perform a CPU recording
1. Stop recording by clicking the Stop button in the bottom of profiler window (not the button next to profiling config dropdown menu)
1. Ensure the recording is selected.
1. Press 'Esc' the selection should clear.
1. click the clock icon in the lower-left corner of a capture. The capture will be selected.
1. Press 'Esc' twice
1. click the clock icon in the lower-left corner of a capture. The capture will be selected.
1. Press 'Esc' once
1. **Validate the selection is cleared.**

![CPU - Trace Selection][cpu-trace-selection]

---

![CPU - Import Trace][session-import]

![CPU - Select Process][cpu-trace-import]

## Memory

### Java Memory Allocaion

1. In the "Android Profiler" Toolbar, make sure you are on the memory profiler.
1. In the QA App, select the "Java Memory Allocation" scenario.
1. Press the "run" button
1. You should see **java** memory **increase every 2 seconds 5 times before
   falling back to baseline**

![Memory - Java Memory Allocation][memory-alloc-java]

### Live Allocation

**(This test is expected to work only on Android O+, API >= 26)**

1. In the "Android Profiler" Toolbar, make sure you are on the memory profiler.
1. Choose "Record Java/Kotlin allocations" and press "Record".
1. The profiler should enter a new allocation-recording stage with a smaller timeline on top.
1. In the QA App, select the "Java Memory Allocation" scenario.
1. Observe the sampling pulldown, which should be set to *Full* and there should
be a dashed line in the timeline.
   * Set it to "Sampled" now.
     The dashed line should end with a semi-filled circle.
1. Change the pulldown from "Sampled" to "Full"
1. You should see **the dashed line reappearing, starting with a filled circle**
1. The "force garbage collection" button should still work as in the main memory stage.
1. Press the red "Stop recording" button to stop recording.
1. Press the "back" (left arrow) button to go back to the memory profiler's main stage.
1. You should be able to re-visit this recorded allocation session either by clicking
   on corresponding grayed out period on the main timeline, or by clicking the entry
   in the "Sessions" panel on the left.

![Memory - Live Allocation Stage][memory-alloc-stage]

### Native Memory Allocation

1. In the "Android Profiler" Toolbar, make sure you are on the memory profiler.
1. In the QA App, select the "Native Memory Allocation" scenario.
1. Press the "run" button
1. You should see **native** memory **increase every 2 seconds 5 times before
   falling back to baseline**

![Memory - Native Memory Allocation][memory-alloc-native]

### Object Allocation

1. In the "Android Profiler" Toolbar, make sure you are on the memory profiler.
1. In the QA App, select the "Object Allocation" scenario.
1. Press the "run" button
1. You should see **the number of objects increases every 2 seconds 5 times
   before failing back to baseline**

![Memory - Object Allocation][memory-alloc-object]

### JNI References Allocation

**(This test is expected to work only on Android O+, API >= 26)**

1. In the "Android Profiler" Toolbar, make sure you are on the memory profiler.
1. Choose "Record Java/Kotlin allocations" and press "Record".
1. In the QA App, select the "JNI References Allocation" scenario.
1. Press the "run" button
1. You should see **7 trash cans appearing on the memory graph**
1. Select a region of the memory graph containing all of those 7 trash cans
1. Select **JNI heap** from the drop-down above the class list
1. Find and click **MemoryTaskCategory$AllocationTestObjectOfSize** row in the class list
1. You should see 5000 in the *Allocations* and *Deallocations* columns
1. Click any **JNI Global reference** in the Instance View
1. In the *Allocation Call Stack* section below you should see lines *newRef3*, *newRef2*, *newRef1*
   and *native_memory.cpp* next to them.

![Memory - Object Allocation][memory-jni-app]
![Memory - Object Allocation][memory-jni-studio]

### Export / Import Heap Dump

1. In the "Android Profiler", after you've performed at least one heap dump,
   you can find an option to export it to a file (a save icon present in the
   left hand sessions view).
1. Save the file anywhere you'd like, for example in "~/tmp" on Linux or Mac.
1. In the same sessions panel, press the "+" button. Select the file you've
   just saved.
1. **Check that the memory profiler is populated with the data you just
   exported.**

![Memory - Export Heap Dump][memory-heap-export]

### Heap Selection

1. In the “Android Profiler”, after you’ve performed at least one heap dump.
1. Go to any other profiler (e.g. network)
1. Use the sessions panel to navigate to the heap dump you've created (just click on it)
1. Press 'Esc'
1. **Validate the selection is cleared.**

![Memory - Heap Selection][memory-heap-selection]

---

![Memory - Import Heap Dump][session-import]

## Network

### Http Request

1. In the "Android Profiler" Toolbar, make sure you are on the network profiler.
1. In the QA App, select the "Http Request" scenario.
1. Press the "run" button
1. You should see **five chunks of data downloaded**, with **each chunk
   approximately twice as big as previous one**
1. You can also find that **there is only one more connection**

![Network - Http Request][network-httpurl]

### OkHttp Request

1. In the "Android Profiler" Toolbar, make sure you are on the network profiler.
1. In the QA App, select the "OkHttp Request" scenario.
1. Press the "run" button
1. You should see **five chunks of data downloaded**, with **each chunk
   approximately twice as big as previous one**
1. You can also find that **number of connections is increased by one every
   time a new download task starts**

![Network - OkHttp Request][network-okhttp]

## Energy

**(All energy tests are expected to work only on Android O+, API >= 26)**

### Basic Profiling

#### CPU

1. In the "Android Profiler" Toolbar, make sure you are on the energy profiler.
1. In the QA App, select the "CPU -> Periodic Usage" scenario.
2. Press the "run" button
3. In the energy profiler, you should see **five chunks of heavy energy usage**
   attributed to CPU.

![Energy - CPU][energy-basic-cpu]

#### Network

1. In the "Android Profiler" Toolbar, make sure you are on the energy profiler.
1. In the QA App, select the "Network -> Http Request" scenario.
1. Press the "run" button
1. In the energy profiler, you should see **five chunks of light energy usage**
   attributed to network.

![Energy - Network][energy-basic-network]

#### Location

1. In the "Android Profiler" Toolbar, make sure you are on the energy profiler.
1. In the QA App, select the "Location -> Update fine location with minimal
   interval" scenario.
1. Press the "run" button
1. **Note**: This task takes about 30 seconds to complete.
1. In the energy profiler, you should see **a 30s section of light energy
   usage** attributed to location.

![Energy - Location][energy-basic-location]

### Events

#### Wake Lock

1. In the "Android Profiler" Toolbar, make sure you are on the energy profiler.
1. In the QA App, select the "Background Tasks -> Wake Lock" scenario.
1. Press the "run" button
1. **Note**: This task takes about 30 seconds to complete.
1. In the energy profiler in the system area, you should see **a wake lock
   system event**.
1. Select a range which overlaps the event. This will show a table which should
   include the wake lock event. Select that row.
1. **Ensure a "Wake Lock Details" view appears in a panel on the right and is
   populated with relevant details and callstacks.**

![Energy - Wake Lock][energy-wakelock]

#### Jobs

1. In the "Android Profiler" Toolbar, make sure you are on the energy profiler.
1. In the QA App, select the "Background Tasks -> Single Job" scenario.
1. Press the "run" button
1. In the energy profiler in the system area, you should see **a short job
   system event**.
1. Select a range which overlaps the event. This will show a table which should
   include the job event. Select that row.
1. **Ensure a "Job Details" view appears in a panel on the right and is
   populated with relevant details and callstacks.**

![Energy - Jobs][energy-job]

#### Location

1. In the "Android Profiler" Toolbar, make sure you are on the energy profiler.
1. In the QA App, select the "Location -> Update fine location with minimal
   interval" scenario.
1. Press the "run" button
4. **Note**: This task takes about 30 seconds to complete.
5. In the energy profiler in the system area, you should see **a location
   system event**.
6. Select a range which overlaps the event. This will show a table which should
   include the location event. Select that row.
7. **Ensure a "Location Details" view appears in a panel on the right and is
   populated with relevant details and callstacks.**

![Energy - Location][energy-location]

## Events

### Basic Events

To test basic events, just interact with the app and make sure the right icon appears on the event area at the top
of whichever profiler you have selected.

1. In the QA App, select the "Basic Events" scenario.
   * This is actually optional - events will be detected in any scenario. This
     scenario simply exists as a reminder to test them.
1. Tap the screen and you **should see a pink circle**
1. Tap and hold the screen and you **should see an extended pink circle**
1. Make sure the phone or AVD's system-wide Auto-rotate setting is on
1. Rotate the screen and when the app reacts to the rotation, you **should see a "rotation" icon**

![Events - Basic][events-basic]

### Switch Activities

1. In the QA App, select the "Switch Activities" scenario.
1. Press the "run" button
1. In the QA App, the **screen will be replaced with another activity**.
   * **Note:** You can ignore the contents of the activity for this test.
1. On the event profiler, **MainActivity becomes "saved - stopped" and
   fragment.FragmentHostActivity starts**. You can also see a **pink
   circle** since we tapped the screen.

![Events - Enter Activty][events-activity-enter]

1. Hit "back" button to return back to the main activity
1. On the event profiler, **fragment.FragmentHostActivity becomes
   "stopped - destroyed" and MainActivity starts**. You can also
   see a **back icon** since we pressed the "back" button.

![Events - Exit Activity][events-activity-exit]

### Fragment Indicators

**(This test is expected to work only on Android O+, API >= 26)**

1. In the QA App, select the "Switch Activities" scenario.
1. Press the "run" button
1. In the QA App, the **screen will be replaced with a fragment hosting
   activity**.
1. Press the "Navigate" button a couple of times, waiting a few seconds
   between presses.
   * (This will not change the current activity but will toggle which fragment
   is active within the activity, each time the button is pressed.)
1. On the event profiler, observe that the Activity bar is now **tagged with
   markers indicating when fragments were started and stopped.** You can also
   see **pink circles** above these locations since we tapped the screen.
1. Hover the cursor anywhere over the FragmentHostActivity bar and note that
   there will be a fragment (either FragmentA or FragmentB) listed in the tooltips.

![Events - Enter Activty][events-fragment-markers]

[toolbar]: res/perf-tools/toolbar.png
[app]: res/perf-tools/app.png
[cpu-periodic]: res/perf-tools/cpu-periodic.png
[cpu-file-write]: res/perf-tools/cpu-file-write.png
[cpu-native]: res/perf-tools/cpu-native.png
[cpu-native-filter]: res/perf-tools/cpu-native-filter.png
[cpu-startup-config]: res/perf-tools/cpu-startup-config.png
[cpu-startup]: res/perf-tools/cpu-startup.png
[cpu-systrace]: res/perf-tools/cpu-systrace.png
[cpu-automatic]: res/perf-tools/cpu-automatic.png
[cpu-trace-export]: res/perf-tools/cpu-trace-export.png
[cpu-trace-import]: res/perf-tools/cpu-trace-import.png
[cpu-trace-selection]: res/perf-tools/cpu-trace-selection.png
[memory-alloc-java]: res/perf-tools/memory-alloc-java.png
[memory-alloc-native]: res/perf-tools/memory-alloc-native.png
[memory-alloc-object]: res/perf-tools/memory-alloc-object.png
[memory-alloc-stage]: res/perf-tools/memory-alloc-stage.png
[memory-jni-app]: res/perf-tools/memory-jni-app.png
[memory-jni-studio]: res/perf-tools/memory-jni-studio.png
[memory-heap-export]: res/perf-tools/memory-heap-export.png
[memory-heap-selection]: res/perf-tools/memory-alloc-java.png
[network-httpurl]: res/perf-tools/network-httpurl.png
[network-okhttp]: res/perf-tools/network-okhttp.png
[energy-basic-cpu]: res/perf-tools/energy-basic-cpu.png
[energy-basic-network]: res/perf-tools/energy-basic-network.png
[energy-basic-location]: res/perf-tools/energy-basic-location.png
[energy-wakelock]: res/perf-tools/energy-wakelock.png
[energy-job]: res/perf-tools/energy-job.png
[energy-location]: res/perf-tools/energy-location.png
[events-basic]: res/perf-tools/events-basic.png
[events-typing]: res/perf-tools/events-typing.png
[events-activity-enter]: res/perf-tools/events-activity-enter.png
[events-activity-exit]: res/perf-tools/events-activity-exit.png
[events-fragment-markers]: res/perf-tools/events-fragment-markers.png
[session-import]: res/perf-tools/session-import.png
