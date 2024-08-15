licenses(["notice"])

java_library(
    name = "common",
    srcs = glob(["*.java"]),
    visibility = ["//tools/adt/idea/aswb/shared:__subpackages__"],
    deps = [
        "//prebuilts/tools/common/m2:jsr305-2.0.1",
        "//tools/vendor/google/aswb/third_party/java/auto:auto_value",
        "@maven//:com.google.guava.guava",
    ],
)
