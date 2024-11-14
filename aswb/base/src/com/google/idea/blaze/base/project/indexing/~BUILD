load("//tools/adt/idea/aswb/build_defs:build_defs.bzl", "intellij_plugin_library")
load("//tools/base/bazel:kotlin.bzl", "kt_jvm_library")

kt_jvm_library(
    name = "indexing",
    srcs = glob([
        "*.kt",
        "*.java",
    ]),
    visibility = [
        "//tools/adt/idea/aswb/base:__subpackages__",
    ],
    deps = [
        "//tools/adt/idea/aswb/base",
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",  # unuseddeps: keep
    ],
)
