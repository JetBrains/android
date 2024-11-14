load("//tools/base/bazel:proto.bzl", "android_java_proto_library")

android_java_proto_library(
    name = "play_vitals_java_proto",
    srcs = glob(["**/*.proto"]),
    grpc_support = True,
    java_deps = ["@maven//:io.grpc.grpc-all"],
    visibility = ["//visibility:public"],
)
