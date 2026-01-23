# Project: Screen Sharing Agent

## Overview

The `screen-sharing-agent` is a native Android application designed to facilitate screen mirroring and remote control functionality for Android Studio. It acts as an on-device agent that captures screen video and audio, and accepts input and control commands from a host (Android Studio).

## Architecture

*   **Type:** Hybrid Android Application (Java wrapper + Native C++ Shared Library).
*   **Entry Point:** The application is launched via `app_process` invoking `com.android.tools.screensharing.Main`. This Java class loads the native library `libscreen-sharing-agent.so` and transfers control to the native `agent.cc`.
*   **Core Logic:**
    *   **Native Agent (`agent.cc`):** Initializes the environment, manages sockets, and runs the main event loop.
    *   **Communication:** Connects to Unix domain sockets (video, audio, control) in the abstract namespace.
    *   **Protocol:** Uses a custom binary protocol defined in `control_messages.h` for exchanging commands and data (input events, display config, clipboard sync, etc.).
    *   **Streaming:** Supports video (VP8/AVC/HEVC) and audio streaming.
    *   **Input Injection:** Injects motion, key, and text events into the system.
    *   **XR Support:** Includes specific handling for XR devices (head pose, passthrough, etc.).

## Key Files

*   **`app/src/main/cpp/agent.cc`**: The heart of the agent. Handles initialization, socket connections, and the main run loop.
*   **`app/src/main/cpp/main.cc`**: Contains the JNI entry point `Java_com_android_tools_screensharing_Main_nativeMain`.
*   **`app/src/main/cpp/control_messages.h`**: Defines the classes for the binary control protocol (requests and responses).
*   **`app/src/main/java/com/android/tools/screensharing/Main.java`**: The Java class responsible for loading the native library and starting the native agent.
*   **`app/src/main/cpp/CMakeLists.txt`**: CMake build configuration for the native library.
*   **`app/build.gradle.kts`**: Android Gradle configuration.

## Building and Running

### Build

The project uses Gradle with CMake for native compilation.

```bash
./gradlew assembleDebug
```

This will build the APK and the underlying shared library.

### Execution Model

This agent is typically not installed as a regular user app but pushed to the device (e.g., to `/data/local/tmp/`) and executed directly via shell commands from Android Studio.

Example conceptual launch sequence:
1.  Push artifacts to device.
2.  Run via `app_process`:
    ```bash
    CLASSPATH=... app_process /system/bin com.android.tools.screensharing.Main --socket=...
    ```

## Development Conventions

*   **Language Standards:** C++20 for native code, Java 8 for the launcher.
*   **Code Style:** Follows Google C++ Style Guide and Android coding conventions.
*   **Dependencies:** Relies on Android NDK libraries (`aaudio`, `camera2ndk`, `log`, `mediandk`, `android`).
