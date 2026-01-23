java_library(
    name = "proto",
    srcs = glob(["*.java"]),
    visibility = ["//tools/adt/idea/aswb/shared:__subpackages__"],
    deps = [
        "//tools/adt/idea/aswb/shared/java/com/google/idea/blaze/common",
        "//tools/vendor/google/aswb/plugin_api/maven:guava",
        "@maven//:io.grpc.grpc-protobuf",
    ],
)
