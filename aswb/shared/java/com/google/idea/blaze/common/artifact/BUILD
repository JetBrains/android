licenses(["notice"])

java_library(
    name = "artifact",
    srcs = glob(["*.java"]),
    visibility = [
        "//tools/adt/idea/aswb/base:__subpackages__",
        "//tools/adt/idea/aswb/shared:__subpackages__",
        "//tools/vendor/google3/aswb/javatests/com/google/devtools/intellij/blaze/plugin/aswb:__subpackages__",
    ],
    deps = [
        "//prebuilts/tools/common/m2:jsr305-2.0.1",
        "//tools/adt/idea/aswb/shared",
        "@maven//:com.google.guava.guava",
    ],
)
