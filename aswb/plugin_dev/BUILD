load(
    "//tools/adt/idea/aswb:build-visibility.bzl",
    "PLUGIN_PACKAGES_VISIBILITY",
)
load(
    "//tools/adt/idea/aswb/build_defs:build_defs.bzl",
    "intellij_plugin_library",
    "optional_plugin_xml",
)

licenses(["notice"])

java_library(
    name = "plugin_dev",
    srcs = glob(["src/**/*.java"]),
    deps = [
        "//tools/adt/idea/aswb/base",
        "//tools/adt/idea/aswb/common/experiments",
        "//tools/adt/idea/aswb/common/util:transactions",
        "//tools/adt/idea/aswb/java",
        "//tools/adt/idea/aswb/proto:proto_deps",
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/shared:artifact",
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:devkit",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
    ],
)

optional_plugin_xml(
    name = "optional_xml",
    module = "DevKit",
    plugin_xml = "src/META-INF/blaze-plugin-dev.xml",
)

intellij_plugin_library(
    name = "plugin_library",
    optional_plugin_xmls = [":optional_xml"],
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [":plugin_dev"],
)

intellij_plugin_library(
    name = "empty",
)
