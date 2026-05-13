load("//tools/adt/idea/aswb/build_defs:build_defs.bzl", "aswb_library")

licenses(["notice"])

aswb_library(
    name = "artifact",
    srcs = glob([
        "*.java",
        "*.kt",
    ]),
    visibility = [
        "//tools/adt/idea/aswb:__subpackages__",
        "//tools/vendor/google3/aswb/javatests/com/google/devtools/intellij/blaze/plugin/aswb:__subpackages__",
    ],
    deps = [
        "//prebuilts/tools/common/m2:jsr305-2.0.1",
        "//tools/adt/idea/aswb/plugin_api",
        "//tools/adt/idea/aswb/shared",
    ],
)
