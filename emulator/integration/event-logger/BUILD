PACKAGE = "com.android.tools.eventlogger"

MANIFEST = "app/src/main/AndroidManifest.xml"

android_binary(
    name = "event-logger",
    custom_package = PACKAGE,
    manifest = MANIFEST,
    manifest_values = {
        "minSdkVersion": "21",
        "versionCode": "1",
        "versionName": "0.1",
        "targetSdkVersion": "33",
    },
    tags = ["no_windows"],
    visibility = ["//tools/adt/idea/emulator:__pkg__"],
    deps = [
        "//tools/adt/idea/emulator/integration/event-logger/app/src/main/java/com/android/tools/eventlogger:event_logging_activity",
    ],
)
