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
        # This target relies on Android transitions, and only builds as a dependency of android_cc_binary rules.
        # See b/181808614#comment6.
        "no_linux",
    ],
    alwayslink = True,
)

cc_binary(
    name = "screen-sharing-agent.so",
    linkopts = ANDROID_LINKOPTS + [
        "-laaudio",
        "-landroid",
        "-lmediandk",
    ],
    linkshared = True,
    stamp = 0,
    tags = [
        "manual",
        "no_windows",
        # This target relies on Android transitions, and only builds as a dependency of android_cc_binary rules.
        # See b/181808614#comment6.
        "no_linux",
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
    name = "aidl_lib",
    srcs = glob(["app/src/main/aidl/**/*.aidl"]),
    tags = ["no_windows"],
    deps = [
        ":android_extras_lib",
        "//prebuilts/studio/sdk:platforms/latest_jar",
        "//tools/base/annotations",
    ],
)

java_library(
    name = "android_extras_lib",
    srcs = glob(["app/src/main/java/android/**/*.java"]),
    tags = ["no_windows"],
    deps = [
        "//prebuilts/studio/sdk:platforms/latest_jar",
    ],
)

java_library(
    name = "apk_lib",
    srcs = glob(
        ["app/src/main/java/**/*.java"],
        exclude = ["app/src/main/java/android/**/*.java"],
    ),
    tags = ["no_windows"],
    deps = [
        ":aidl_lib",
        ":android_extras_lib",
        "//prebuilts/studio/sdk:platforms/latest_jar",
    ],
)

dex_library(
    name = "screen-sharing-agent",
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
            "native/arm64-v8a/libscreen-sharing-agent.so",
            "native/armeabi-v7a/libscreen-sharing-agent.so",
            "native/x86/libscreen-sharing-agent.so",
            "native/x86_64/libscreen-sharing-agent.so",
            "screen-sharing-agent.jar",
        ],
        "@platforms//os:macos": [
            "native/arm64-v8a/libscreen-sharing-agent.so",
            "native/armeabi-v7a/libscreen-sharing-agent.so",
            "native/x86/libscreen-sharing-agent.so",
            "native/x86_64/libscreen-sharing-agent.so",
            "screen-sharing-agent.jar",
        ],
        "@platforms//os:windows": [],
    }),
    mappings = {
        "tools/adt/idea/streaming/screen-sharing-agent/native/": "screen-sharing-agent/",
        "tools/adt/idea/streaming/screen-sharing-agent/": "screen-sharing-agent/",
    },
    visibility = ["//visibility:public"],
)
