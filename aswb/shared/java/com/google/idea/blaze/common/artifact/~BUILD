licenses(["notice"])

java_library(
    name = "artifact",
    srcs = glob(["*.java"]),
    visibility = ["//tools/adt/idea/aswb/shared:__subpackages__"],
    deps = [
        "//prebuilts/tools/common/m2:jsr305-2.0.1",
        "//tools/adt/idea/aswb/shared",
        "//tools/vendor/google/aswb/plugin_api",
        "@maven//:com.google.guava.guava",
    ],
)
