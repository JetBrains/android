load(
    "//tools/adt/idea/aswb:build-visibility.bzl",
    "COMMON_PLUGINS_VISIBILITY",
)
load(
    "//tools/adt/idea/aswb/build_defs:build_defs.bzl",
    "aswb_library",
)

licenses(["notice"])

aswb_library(
    name = "actions",
    srcs = glob(["src/**/*.java"]),
    visibility = COMMON_PLUGINS_VISIBILITY,
    deps = [
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
        "@maven//:com.google.errorprone.error_prone_annotations",
    ],
)
