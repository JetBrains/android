load("//tools/adt/idea/aswb:build-visibility.bzl", "PLUGIN_PACKAGES_VISIBILITY")
load("//tools/adt/idea/aswb/build_defs:build_defs.bzl", "aswb_library", "intellij_plugin_library")

aswb_library(
    name = "switcher_lib",
    srcs = glob([
        "src/**/*.kt",
    ]),
    visibility = [
        "//tools/adt/idea/aswb:__subpackages__",
        "//tools/vendor/google3/aswb:__subpackages__",
    ],
    deps = [
        "//tools/adt/idea/aswb/plugin_api",
    ],
)

intellij_plugin_library(
    name = "switcher",
    plugin_xmls = ["src/META-INF/switcher.xml"],
    visibility = PLUGIN_PACKAGES_VISIBILITY,
    deps = [
        ":switcher_lib",
    ],
)
