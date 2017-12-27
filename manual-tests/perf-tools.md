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

You use the first pulldown to chose a category (CPU, Memory, Network, Events)
and the second pulldown to prepare a particular scenario (Perodic Usage, File
Writing, etc.)

Once any scenario is selected, press the "run" button to test it. The scenario
will run for a few seconds before stopping, at which point you can observe and
confirm its effects.

For convenience, you can aso press the "previous" and "next" buttons to
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

![CPU - Periodic Usage][cpu1]

### File Writing

1. In the "Android Profiler" Toolbar, make sure you are on the CPU profiler.
1. In the QA App, select the "File Writing" scenario.
1. Press the "run" button
1. You should see **four threads perform "writing" with running (green) and
   waiting state (light green)**
1. Wait 2 seconds after all writing threads stop running.
1. You should see **four threads reading the file.**

![CPU - File Writing][cpu2]

## Memory

### Java Memory Allocaion

1. In the "Android Profiler" Toolbar, make sure you are on the memory profiler.
1. In the QA App, select the "Java Memory Allocation" scenario.
1. Press the "run" button
1. You should see **java** memory **increase every 2 seconds 5 times before
   falling back to baseline**

![Memory - Java Memory Allocation][memory1]
### Native Memory Allocaion

1. In the "Android Profiler" Toolbar, make sure you are on the memory profiler.
1. In the QA App, select the "Native Memory Allocation" scenario.
1. Press the "run" button
1. You should see **native** memory **increase every 2 seconds 5 times before
   falling back to baseline**

![Memory - Native Memory Allocation][memory2]

### Object Allocation

1. In the "Android Profiler" Toolbar, make sure you are on the memory profiler.
1. In the QA App, select the "Object Allocation" scenario.
1. Press the "run" button
1. You should see **the number of objects increases every 2 seconds 5 times
   before failing back to baseline**

![Memory - Object Allocation][memory3]

## Network

### Http Request

1. In the "Android Profiler" Toolbar, make sure you are on the network profiler.
1. In the QA App, select the "Http Request" scenario.
1. Press the "run" button
1. You should see **five chunks of data downloaded**, with **each chunk
   approximately twice as big as previous one**
1. You can also find that **there is only one more connection**

![Network - Http Request][network1]

### OkHttp Request

1. In the "Android Profiler" Toolbar, make sure you are on the network profiler.
1. In the QA App, select the "OkHttp Request" scenario.
1. Press the "run" button
1. You should see **five chunks of data downloaded**, with **each chunk
   approximately twice as big as previous one**
1. You can also find that **number of connections is increased by one every
   time a new download task starts**

![Network - OkHttp Request][network2]

## Events

### Basic Events

To test basic events, just interact with the app and make sure the right icon appears on the event area at the top
of whichever profiler you have selected.

1. In the QA App, select the "Basic Events" scenario.
   * This is actually optional - events will be detected in any scenario. This
     scenario simply exists as a reminder to test them.
1. Tap the screen and you **should see a purple circle**
1. Tap and hold the screen and you **should see an extended purple circle**
1. Press volume down and you **should see a "volume up" icon**
1. Press volume up and you **should see a "volume down" icon**
1. Rotate the screen and you **should see a "rotation" icon**

![Events - Basic][event]

### Type Words

1. In the QA App, select the "Type Words" scenario.
1. Press the "run" button
1. Tap the text area to bring up the keyboard
1. Type "e" and you can see the **e icon**
1. Type Backspace and you can see a **delete iconm**
1. Type "l", "o" and you can see **"l" and "lo" icons**
1. Tap the autocompleted word "love" and you can see **the final "love" icon**
1. Tap the "back" button to hide keyboard

![Events - Type Words][type]

### Switch Activities

1. In the QA App, select the "Switch Activities" scenario.
1. Press the "run" button
1. In the QA App, the **screen will be replaced with an empty activity**.
1. On the event profiler, **MainActivity becomes "saved and stopped" and
   event.EmptyActivity starts**. You can also see a **purple dot** since we
   tapped the screen.

![Events - Enter Activty][event1]

1. Hit "back" button to return back to the main activity
1. On tge event profiler, **event.EmptyActivity becomes "stopped and destroyed"
   and MainActivity starts**. You can also see a **back icon** since we pressed
   the "back" button

![Events - Exit Activity][event2]

[toolbar]: res/perf-tools/toolbar.png
[app]: res/perf-tools/app.png
[cpu1]: res/perf-tools/cpu1.png
[cpu2]: res/perf-tools/cpu2.png
[memory1]: res/perf-tools/memory1.png
[memory2]: res/perf-tools/memory2.png
[memory3]: res/perf-tools/memory3.png
[network1]: res/perf-tools/network1.png
[network2]: res/perf-tools/network2.png
[event]: res/perf-tools/event.png
[event1]: res/perf-tools/event1.png
[type]: res/perf-tools/type.png
[event2]: res/perf-tools/event2.png