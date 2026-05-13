load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "proto",
    srcs = glob(["*.java"]),
    visibility = ["//tools/adt/idea/aswb/shared:__subpackages__"],
    deps = [
        "//tools/adt/idea/aswb/plugin_api",
        "//tools/adt/idea/aswb/shared/java/com/google/idea/blaze/common",
    ],
)
