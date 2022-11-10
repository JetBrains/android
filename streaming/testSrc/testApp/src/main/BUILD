package(default_visibility = ["//visibility:private"])

PACKAGE = "com.android.tools.screensharing.testapp"

MANIFEST = "AndroidManifest.xml"

android_binary(
    name = "testapp",
    custom_package = PACKAGE,
    manifest = MANIFEST,
    manifest_values = {
        "minSdkVersion": "21",
        "versionCode": "1",
        "versionName": "0.1",
        "targetSdkVersion": "31",
    },
    tags = ["no_windows"],
    deps = [
        "//tools/adt/idea/streaming/testSrc/testApp/src/main/java/com/android/tools/screensharing/testapp:main_activity",
    ],
)
