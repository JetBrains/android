load("//tools/base/bazel:proto.bzl", "kotlin_proto_library")

package(default_visibility = ["//visibility:public"])

kotlin_proto_library(
    name = "play_vitals_kt_proto",
    srcs = glob(["**/*.proto"]),
    grpc_support = True,
    proto_deps = ["//tools/base/bazel:grpc-common-java_proto_srcs"],
)
