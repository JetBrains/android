load("@rules_java//java:defs.bzl", "java_proto_library")

package(default_visibility = ["//visibility:public"])

filegroup(
    name = "srcs",
    srcs = glob(["*"]),
    visibility = ["//src:__subpackages__"],
)

proto_library(
    name = "package_metrics_proto",
    srcs = ["package_metrics.proto"],
    deps = [
        "@com_google_protobuf//:duration_proto",
    ],
)

java_proto_library(
    name = "package_metrics_java_proto",
    deps = [
        ":package_metrics_proto",
    ],
)

proto_library(
    name = "package_load_metrics_proto",
    srcs = ["package_load_metrics.proto"],
    deps = [
        "@com_google_protobuf//:duration_proto",
    ],
)

java_proto_library(
    name = "package_load_metrics_java_proto",
    deps = [
        ":package_load_metrics_proto",
    ],
)
