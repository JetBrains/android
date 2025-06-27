load("//tools/adt/idea/studio:studio.bzl", "studio_data")
load("@rules_android//rules:rules.bzl", "android_library")
load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "ANDROID_LINKOPTS", "android_cc_binary", "dex_library")

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
    target_compatible_with = ["@platforms//os:android"],
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
    target_compatible_with = ["@platforms//os:android"],
    deps = [
        ":screen-sharing-agent_lib",
    ],
)

android_cc_binary(
    name = "native",
    binary = ":screen-sharing-agent.so",
    filename = "libscreen-sharing-agent.so",
    target_compatible_with = select({
        "@platforms//os:windows": ["@platforms//:incompatible"],
        "//conditions:default": [],
    }),
    visibility = ["//visibility:public"],
)

android_library(
    name = "aidl_lib",
    idl_import_root = "app/src/main/aidl",
    idl_srcs = glob(["app/src/main/aidl/**/*.aidl"]),
    target_compatible_with = select({
        "@platforms//os:windows": ["@platforms//:incompatible"],
        "//conditions:default": [],
    }),
    deps = [
        "//prebuilts/studio/sdk:platforms/latest_jar",
        "//tools/base/annotations",
    ],
)

java_library(
    name = "apk_lib",
    srcs = glob(
        ["app/src/main/java/**/*.java"],
        exclude = ["app/src/main/java/android/**/*.java"],
    ),
    deps = [
        ":aidl_lib",
        "//prebuilts/studio/sdk:platforms/latest_jar",
    ],
)

dex_library(
    name = "screen-sharing-agent",
    flags = ["--min-api 26"],
    jars = [
        ":apk_lib",
    ],
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
