load("//tools/base/bazel:jvm_import.bzl", "jvm_import")
load("//tools/base/bazel:proto.bzl", "kotlin_proto_library")

package(default_visibility = ["//visibility:public"])

exports_files(["cli_commands.proto"])

kotlin_proto_library(
    name = "cli_commands_proto",
    srcs = ["cli_commands.proto"],
    grpc_support = True,
    proto_deps = ["//tools/base/bazel:grpc-common-java_proto_srcs"],
)
