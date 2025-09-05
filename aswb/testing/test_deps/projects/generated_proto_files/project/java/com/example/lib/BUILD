load("@bazel_skylib//rules:copy_file.bzl", "copy_file")
load("@protobuf//bazel:java_proto_library.bzl", "java_proto_library")
load("@protobuf//bazel:proto_library.bzl", "proto_library")

copy_file(
    name = "generate_proto",
    src = "lib.proto_",
    out = "lib.proto",
)

proto_library(
    name = "lib_proto",
    srcs = [":generate_proto"],
)

java_proto_library(
    name = "lib_java_proto",
    visibility = ["//visibility:public"],
    deps = [
        ":lib_proto",
    ],
)
