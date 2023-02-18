### Building
```
bazel build //tools/adt/idea/streaming/integration/event-logger
```
### Installation
```
adb install bazel-bin/tools/adt/idea/streaming/integration/event-logger/event-logger.apk
```

### Run
```
adb shell am start -n com.android.tools.eventlogger/com.android.tools.eventlogger.EventLoggingActivity
```

### Viewing logged messages

Use Logcat filter: `tag:EventLogger`
