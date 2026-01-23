load("//tools/adt/idea/aswb/build_defs:build_defs.bzl", "aswb_library")
load("//tools/base/bazel:proto.bzl", "java_proto_library")

package(default_visibility = ["//visibility:public"])

aswb_library(
    name = "update",
    srcs = glob([
        "*.java",
        "*.kt",
    ]),
    deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/artifacts",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/deps",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project",
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/shared/java/com/google/idea/blaze/common",
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api/maven:guava",
        "@maven//:io.grpc.grpc-protobuf",
        "@maven//:io.grpc.grpc-protobuf-lite",
    ],
)
