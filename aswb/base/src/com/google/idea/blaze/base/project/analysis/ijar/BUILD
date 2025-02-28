load("//tools/adt/idea/aswb/build_defs:build_defs.bzl", "aswb_library")
load(
    "//tools/adt/idea/aswb:build-visibility.bzl",
    "G3PLUGINS_VISIBILITY",
)

aswb_library(
    name = "ijar",
    srcs = glob([
        "*.kt",
        "*.java",
    ]),
    visibility = G3PLUGINS_VISIBILITY,
    deps = [
        "//tools/adt/idea/aswb/common/experiments",
        "//tools/adt/idea/aswb/querysync",
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",  # unuseddeps: keep
    ],
)
