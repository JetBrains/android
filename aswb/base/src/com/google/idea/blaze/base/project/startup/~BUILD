load("//tools/base/bazel:kotlin.bzl", "kt_jvm_library")

kt_jvm_library(
    name = "startup",
    srcs = glob([
        "*.kt",
        "*.java",
    ]),
    visibility = [
        "//visibility:public",
    ],
    deps = [
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",  # unuseddeps: keep
    ],
)
