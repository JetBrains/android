load(
    "//tools/adt/idea/aswb:build-visibility.bzl",
    "PLUGIN_PACKAGES_VISIBILITY",
)
load(
    "//tools/adt/idea/aswb/build_defs:build_defs.bzl",
    "aswb_library",
)

aswb_library(
    name = "vcs",
    srcs = glob(["src/**/*.java"]),
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [
        "//tools/adt/idea/aswb/base",
        "//tools/adt/idea/aswb/common/experiments",
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
    ],
)
