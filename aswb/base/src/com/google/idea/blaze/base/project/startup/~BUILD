load("//tools/adt/idea/aswb/build_defs:build_defs.bzl", "aswb_library")

aswb_library(
    name = "startup",
    srcs = glob([
        "*.kt",
        "*.java",
    ]),
    visibility = [
        "//tools/adt/idea/aswb/base:__subpackages__",
    ],
    deps = [
        "//tools/adt/idea/aswb/plugin_api",
        "//tools/adt/idea/aswb/plugin_api:jsr305",  # unuseddeps: keep
    ],
)
