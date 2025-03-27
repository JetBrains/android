load("//tools/base/bazel:proto.bzl", "studio_java_proto_library")

studio_java_proto_library(
    name = "play_vitals_java_proto",
    srcs = glob(["**/*.proto"]),
    grpc_support = True,
    java_deps = ["@maven//:io.grpc.grpc-all"],
    proto_deps = ["//tools/base/bazel:grpc-common-java_proto_srcs"],
    visibility = ["//visibility:public"],
)
