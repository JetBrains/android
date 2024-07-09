### Building
```
bazel build //tools/adt/idea/streaming/integration/languages
```

### Installation
```
adb install bazel-bin/tools/adt/idea/streaming/integration/languages/languages.apk
```

### Run
```
adb shell am start -n com.android.tools.eventlogger/com.android.tools.languages.MainActivity
```
