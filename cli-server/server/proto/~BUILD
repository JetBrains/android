load("//tools/base/bazel:proto.bzl", "kotlin_proto_library")

package(default_visibility = ["//visibility:public"])

exports_files(["studio_cli_service.proto"])

kotlin_proto_library(
    name = "studio_cli_service_kt_proto",
    srcs = ["studio_cli_service.proto"],
    grpc_support = True,
    proto_deps = ["//tools/base/bazel:grpc-common-java_proto_srcs"],
)
