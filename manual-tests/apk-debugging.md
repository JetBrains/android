# APK Debugging Manual Tests

## Tests for Android Studio 3.4+

### APK Debugging validates the build ID of symbol files

Bug: [b/117609317](http://b/117609317)

1. From Android Studio, create a new C++ project called `ApkTest1`.
1. Click `Build/Build APK(s)/Build APK(s)`.
1. Click `File/Close project`.
1. Click `Profile or Debug APK`.
1. Select `ApkTest1/app/build/outputs/apk/debug/app-debug.apk`.
1. If you are asked, select `Use existing folder`.
1. Open `ApkTest1` project in a new Android Studio window:
    1. Click `File/Open Recent/ApkTest1` and open it in `New Window`.
    1. Edit `app/cpp/native-lib.cpp` and modify the value of `hello` (e.g., `"xyz"`) and save the file.
    1. Click `Build/Build APK(s)/Build APK(s)`.
    1. Click `File/Close Project`.
1. Click `Add debug symbols`.
1. Click `Add` and select the directory: `ApkTest1/app/build/intermediates/cmake/debug/obj`.

**Expected Result:**

  * Event log displays error message: `Unable to find any debuggable shared object files from the selection...`
  * Expanded event log displays warning: `Unable to match build ID for file(s):` with a list of `.so` files.


### APK Debugging remembers the path of the most recently used symbol file across APKs

Bug: Part of [b/117504818](http://b/117504818)

1. From Android Studio, create a new C++ project called `ApkTest2`.
1. Click `Build/Build APK(s)/Build APK(s)`.
1. Click `File/Close Project`.
1. Click `Profile or Debug APK`.
1. Select `ApkTest2/app/build/outputs/apk/debug/app-debug.apk`.
1. If you are asked, select `Use existing folder`.
1. Click `Add debug symbols`.
1. Click `Add` and select the directory: `ApkTest2/app/build/intermediates/cmake/debug/obj`.
1. Expect symbol files to be populated in the panel. Expect no errors in the event log.
1. Click `File/Close Project`.
1. Click `Profile or Debug APK`.
1. Select `ApkTest2/app/build/outputs/apk/debug/app-debug.apk`.
1. If you are asked, select `Use existing folder`.
1. Click `Add debug symbols`.
1. Click `Add`.

**Expected Result:**

When the file chooser window opens, the default location it opens is: `ApkTest2/app/build/intermediates/cmake/debug/obj`.

### APK Debugging users can edit run configurations

Bug: [b/117577669](http://b/117577669)

1. From Android Studio, create a new C++ project called `ApkTest3`.
1. Click `Build/Build APK(s)/Build APK(s)`.
1. Click `File/Close Project`.
1. Click `Profile or Debug APK`.
1. Select `ApkTest3/app/build/outputs/apk/debug/app-debug.apk`.
1. If you are asked, select `Use existing folder`.
1. Click `Add debug symbols`.
1. Click `Add` and select the directory: `ApkTest3/app/build/intermediates/cmake/debug/obj`.
1. Expect symbol files to be populated in the panel. Expect no errors in the event log.
1. Click `Run/Edit Configurations`.
1. Toggle the checkbox `Warn when debugging optimized code` and save (exact truth value of checkbox is not important).
1. Click `OK`.
1. Open `native-lib.cpp` file. You might need to switch to `Project` view to find the file.
1. Set breakpoint at the line of the `return` statement (around line 9).
1. Hit `Run/Debug 'app'`. Select an appropriate device (physical ARM device, or Emulator x86 device).

**Expected Result:**
The app gets installed, and then the breakpoint hits.

### APK Debugging generates informative log when building APKs from multiple modules

Bug: [b/118501595](http://b/118501595)

1. Clone the following git project: https://github.com/googlesamples/android-ndk.git
1. Open existing Android Studio project at: `android-ndk/teapots`.
1. Click `Build/Build APK(s)/Build APK(s)`.

**Expected Result:**

  * The event log says: `APK(s) generated successfully for 4 modules:`
  * Expanded event log also lists the modules and has analyze/locate links.

