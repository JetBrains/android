load("//tools/adt/idea/aswb/build_defs:build_defs.bzl", "aswb_library")

licenses(["notice"])

aswb_library(
    name = "common",
    srcs = glob([
        "*.java",
        "*.kt",
    ]),
    visibility = [
        "//visibility:public",
    ],
    deps = [
        "//prebuilts/tools/common/m2:jsr305-2.0.1",
        "//tools/adt/idea/aswb/third_party/java/auto_value",
        "//tools/vendor/google/aswb/plugin_api",
        "@maven//:com.google.guava.guava",
    ],
)
