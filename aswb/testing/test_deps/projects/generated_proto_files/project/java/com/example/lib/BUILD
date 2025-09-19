load("@bazel_skylib//rules:copy_file.bzl", "copy_file")
load("@protobuf//bazel:java_proto_library.bzl", "java_proto_library")
load("@protobuf//bazel:proto_library.bzl", "proto_library")
load(":rule.bzl", "custom_rule")

copy_file(
    name = "generate_proto",
    src = "lib.proto_",
    out = "lib.proto",
)

custom_rule(
    name = "foo",
    visibility = ["//visibility:public"],
    deps = [
        "//generated_proto_files/project/java/com/example/lib:libFoo.jar",
    ],
)

java_library(
    name = "foo_src",
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
