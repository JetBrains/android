load("//tools/adt/idea/studio:studio.bzl", "studio_data")
load("//tools/base/bazel:android.bzl", "ANDROID_LINKOPTS", "android_cc_binary")

cc_binary(
    name = "process-tracker",
    srcs = glob(["**/*.cc"]),
    linkopts = ANDROID_LINKOPTS,
    stamp = 0,
    tags = [
        "manual",
        "no_windows",
    ],
)

android_cc_binary(
    name = "native",
    abis = [
        "x86",
        "x86_64",
        "armeabi-v7a",
        "arm64-v8a",
    ],
    binary = ":process-tracker",
    filename = "process-tracker",
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
)

studio_data(
    name = "bundle",
    files = select({
        "@platforms//os:linux": [
            "native/arm64-v8a/process-tracker",
            "native/armeabi-v7a/process-tracker",
            "native/x86/process-tracker",
            "native/x86_64/process-tracker",
        ],
        "@platforms//os:macos": [
            "screen-sharing-agent.jar",
            "native/arm64-v8a/process-tracker",
            "native/armeabi-v7a/process-tracker",
            "native/x86/process-tracker",
            "native/x86_64/process-tracker",
        ],
        "@platforms//os:windows": [],
    }),
    mappings = {
        "tools/adt/idea/process-monitor/process-tracker-agent/native/": "process-tracker/",
    },
    visibility = ["//visibility:public"],
)
