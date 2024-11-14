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
    name = "terminal",
    srcs = glob(["src/**/*.java"]),
    deps = [
        "//tools/adt/idea/aswb/base",
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
        "//tools/vendor/google/aswb/plugin_api:terminal",
    ],
)

optional_plugin_xml(
    name = "optional_xml",
    module = "org.jetbrains.plugins.terminal",
    plugin_xml = "src/META-INF/terminal-contents.xml",
)

intellij_plugin_library(
    name = "plugin_library",
    optional_plugin_xmls = [":optional_xml"],
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [":terminal"],
)
