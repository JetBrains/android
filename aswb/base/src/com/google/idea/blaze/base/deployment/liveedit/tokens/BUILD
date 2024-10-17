load("//tools/base/bazel:kotlin.bzl", "kt_jvm_library")

kt_jvm_library(
    name = "tokens",
    srcs = glob([
        "*.kt",
    ]),
    visibility = [
        "//tools/adt/idea/aswb/base:__subpackages__",
    ],
    deps = [
        "//tools/adt/idea/aswb/aswb:aswb_lib",
        "//tools/adt/idea/aswb/base",
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",  # unuseddeps: keep
    ],
)
