When `ImageConverter.c` is changed, the prebuilt libimage_converter native libraries has to be updated for all platforms. To update the libimage_converter library for the current platform, run
```
bazel build //tools/adt/idea/streaming/native:update_libimage_converter
```

**Note:** The Mac Arm version of the library (`tools/adt/idea/streaming/native/mac_arm/libimage_converter.dylib`) cannot be built using the above method yet. The current version was obtained from an Emulator build (https://android-build.googleplex.com/builds/branches/aosp-emu-master-dev/grid).
