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
    name = "skylark_lib",
    srcs = glob(["src/**/*.java"]),
    visibility = [
        "//tools/adt/idea/aswb:__subpackages__",
        "//tools/vendor/google3/aswb:__subpackages__",
    ],
    deps = [
        "//tools/adt/idea/aswb/base",
        "//tools/adt/idea/aswb/common/experiments",
        "//tools/adt/idea/aswb/plugin_api",
        "//tools/adt/idea/aswb/plugin_api:jsr305",
        "//tools/adt/idea/aswb/proto:proto_deps",
    ],
)

intellij_plugin_library(
    name = "skylark",
    plugin_xmls = ["src/META-INF/blaze-skylark.xml"],
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [":skylark_lib"],
)
