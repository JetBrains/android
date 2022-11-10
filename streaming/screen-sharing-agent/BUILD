load("//tools/adt/idea/studio:studio.bzl", "studio_data")
load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "ANDROID_LINKOPTS", "aidl_library", "android_cc_binary", "dex_library")

cc_library(
    name = "screen-sharing-agent_lib",
    srcs = glob(["app/src/main/cpp/**/*.cc"]),
    hdrs = glob(["app/src/main/cpp/**/*.h"]),
    copts = ANDROID_COPTS + [
        "-Wno-macro-redefined",
        "-D__ANDROID_API__=26",
    ],
    includes = [
        "app/src/main/cpp",
    ],
    linkopts = ANDROID_LINKOPTS,
    tags = [
        "manual",
        "no_windows",
    ],
    alwayslink = True,
)

cc_binary(
    name = "screen-sharing-agent.so",
    linkopts = ANDROID_LINKOPTS + [
        "-landroid",
        "-lmediandk",
    ],
    linkshared = True,
    stamp = 0,
    tags = [
        "manual",
        "no_windows",
    ],
    deps = [
        ":screen-sharing-agent_lib",
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
    binary = ":screen-sharing-agent.so",
    filename = "libscreen-sharing-agent.so",
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
)

aidl_library(
    name = "aidl_lib1",
    srcs = ["app/src/main/aidl/android/content/IOnPrimaryClipChangedListener.aidl"],
    tags = ["no_windows"],
    deps = [
        "//prebuilts/studio/sdk:platforms/latest_jar",
        "//tools/base/annotations",
    ],
)

aidl_library(
    name = "aidl_lib2",
    srcs = ["app/src/main/aidl/android/view/IRotationWatcher.aidl"],
    tags = ["no_windows"],
    deps = [
        "//prebuilts/studio/sdk:platforms/latest_jar",
        "//tools/base/annotations",
    ],
)

java_library(
    name = "apk_lib",
    srcs = glob(["app/src/main/java/**/*.java"]),
    tags = ["no_windows"],
    deps = [
        ":aidl_lib1",
        ":aidl_lib2",
        "//prebuilts/studio/sdk:platforms/latest_jar",
    ],
)

dex_library(
    name = "screen-sharing-agent",
    dexer = "D8",
    flags = ["--min-api 26"],
    jars = [
        ":apk_lib",
    ],
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
)

studio_data(
    name = "bundle",
    files = select({
        "@platforms//os:linux": [
            "screen-sharing-agent.jar",
            "native/arm64-v8a/libscreen-sharing-agent.so",
            "native/armeabi-v7a/libscreen-sharing-agent.so",
            "native/x86/libscreen-sharing-agent.so",
            "native/x86_64/libscreen-sharing-agent.so",
        ],
        "@platforms//os:macos": [
            "screen-sharing-agent.jar",
            "native/arm64-v8a/libscreen-sharing-agent.so",
            "native/armeabi-v7a/libscreen-sharing-agent.so",
            "native/x86/libscreen-sharing-agent.so",
            "native/x86_64/libscreen-sharing-agent.so",
        ],
        "@platforms//os:windows": [],
    }),
    mappings = {
        "tools/adt/idea/streaming/screen-sharing-agent/native/": "screen-sharing-agent/",
        "tools/adt/idea/streaming/screen-sharing-agent/": "screen-sharing-agent/",
    },
    visibility = ["//visibility:public"],
)
