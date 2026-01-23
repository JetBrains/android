licenses(["notice"])

java_library(
    name = "artifact",
    srcs = glob(["*.java"]),
    visibility = [
        "//tools/adt/idea/aswb:__subpackages__",
        "//tools/vendor/google3/aswb/javatests/com/google/devtools/intellij/blaze/plugin/aswb:__subpackages__",
    ],
    deps = [
        "//prebuilts/tools/common/m2:jsr305-2.0.1",
        "//tools/adt/idea/aswb/shared",
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api/maven:guava",
    ],
)
