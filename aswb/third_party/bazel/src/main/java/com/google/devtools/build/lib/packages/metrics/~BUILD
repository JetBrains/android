load("//tools/base/bazel:proto.bzl", "java_proto_library")

package(default_visibility = ["//visibility:public"])

filegroup(
    name = "srcs",
    srcs = glob(["*"]),
    visibility = ["//src:__subpackages__"],
)

java_proto_library(
    name = "package_metrics_java_proto",
    srcs = ["package_metrics.proto"],
)

java_proto_library(
    name = "package_load_metrics_java_proto",
    srcs = ["package_load_metrics.proto"],
)
