load(
    "//tools/adt/idea/aswb:build-visibility.bzl",
    "G3PLUGINS_VISIBILITY",
)
load("//tools/adt/idea/aswb/build_defs:build_defs.bzl", "aswb_library")

aswb_library(
    name = "ijar",
    srcs = glob([
        "*.kt",
        "*.java",
    ]),
    visibility = ["//tools/adt/idea/aswb/base:__subpackages__"],
    deps = [
        "//tools/adt/idea/aswb/common/experiments",
        "//tools/adt/idea/aswb/plugin_api",
        "//tools/adt/idea/aswb/plugin_api:jsr305",  # unuseddeps: keep
        "//tools/adt/idea/aswb/querysync",
    ],
)
