load("//tools/base/bazel:proto.bzl", "java_proto_library")
load("//tools/base/bazel:utils.bzl", "java_jarjar")

package(default_visibility = ["//visibility:public"])

filegroup(
    name = "srcs",
    srcs = glob(["*"]),
    visibility = ["//src:__subpackages__"],
)

java_proto_library(
    name = "package_metrics_java_proto_internal",
    srcs = ["package_metrics.proto"],
)

java_jarjar(
    name = "package_metrics_java_proto",
    srcs = [
        ":package_metrics_java_proto_internal",
    ],
    rules = "//tools/base/bazel:jarjar_rules.txt",
)

java_proto_library(
    name = "package_load_metrics_java_proto_internal",
    srcs = ["package_load_metrics.proto"],
)

java_jarjar(
    name = "package_load_metrics_java_proto",
    srcs = [
        ":package_load_metrics_java_proto_internal",
    ],
    rules = "//tools/base/bazel:jarjar_rules.txt",
)
