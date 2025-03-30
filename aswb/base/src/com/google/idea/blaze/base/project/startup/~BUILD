load("//tools/adt/idea/aswb/build_defs:build_defs.bzl", "aswb_library")

aswb_library(
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
