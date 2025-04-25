load("@rules_android//android:rules.bzl", "android_binary")

PACKAGE = "com.android.tools.eventlogger"

MANIFEST = "app/src/main/AndroidManifest.xml"

android_binary(
    name = "event-logger",
    srcs = glob(["app/src/main/res/**/*.java"]),
    custom_package = PACKAGE,
    javacopts = [
        "-source",
        "8",
        "-target",
        "8",
    ],
    manifest = MANIFEST,
    manifest_values = {
        "minSdkVersion": "26",
        "versionCode": "1",
        "versionName": "1.0",
        "targetSdkVersion": "36",
    },
    resource_files = glob(["app/src/main/res/**"]),
    target_compatible_with = select({
        "//tools/base/bazel/platforms:windows_host": ["@platforms//:incompatible"],
        "//conditions:default": [],
    }),
    visibility = ["//tools/adt/idea/streaming:__pkg__"],
)
