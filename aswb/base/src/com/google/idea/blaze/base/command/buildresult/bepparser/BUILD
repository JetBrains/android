load(
    "//tools/adt/idea/aswb:build-visibility.bzl",
    "G3PLUGINS_VISIBILITY",
)
load("//tools/adt/idea/aswb/build_defs:build_defs.bzl", "aswb_library")

aswb_library(
    name = "bepparser",
    srcs = glob([
        "*.java",
        "*.kt",
    ]),
    visibility = G3PLUGINS_VISIBILITY,
    deps = [
        "//tools/adt/idea/aswb/common/experiments",
        "//tools/adt/idea/aswb/plugin_api",
        "//tools/adt/idea/aswb/plugin_api:jsr305",  # unuseddeps: keep for @Nullable
        "//tools/adt/idea/aswb/proto:proto_deps",
        "//tools/adt/idea/aswb/shared:artifact",
        "//tools/adt/idea/aswb/shared:exception",
    ],
)
