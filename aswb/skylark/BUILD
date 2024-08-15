#
# Description: A Skylark debugging client for IntelliJ.
#

load(
    "//tools/adt/idea/aswb:build-visibility.bzl",
    "PLUGIN_PACKAGES_VISIBILITY",
    "SKYLARK_PACKAGES_VISIBILITY",
)
load("//tools/adt/idea/aswb/build_defs:build_defs.bzl", "intellij_plugin_library")

licenses(["notice"])

java_library(
    name = "skylark",
    srcs = glob(["src/**/*.java"]),
    visibility = SKYLARK_PACKAGES_VISIBILITY,
    deps = [
        "//tools/adt/idea/aswb/base",
        "//tools/adt/idea/aswb/common/experiments",
        "//tools/adt/idea/aswb/proto:proto_deps",
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
    ],
)

intellij_plugin_library(
    name = "plugin_library",
    plugin_xmls = ["src/META-INF/blaze-skylark.xml"],
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [":skylark"],
)
