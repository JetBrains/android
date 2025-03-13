load("@protobuf//bazel:java_lite_proto_library.bzl", "java_lite_proto_library")
load("@protobuf//bazel:java_proto_library.bzl", "java_proto_library")
load("@protobuf//bazel:proto_library.bzl", "proto_library")

package(
    default_visibility = ["//visibility:public"],
)

proto_library(
    name = "external_proto",
    srcs = ["external.proto"],
)

java_proto_library(
    name = "external_java_proto",
    deps = [
        ":external_proto",
    ],
)

java_lite_proto_library(
    name = "external_java_proto_lite",
    deps = [
        ":external_proto",
    ],
)
