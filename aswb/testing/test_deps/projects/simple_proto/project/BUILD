load("@protobuf//bazel:java_lite_proto_library.bzl", "java_lite_proto_library")
load("@protobuf//bazel:java_proto_library.bzl", "java_proto_library")
load("@protobuf//bazel:proto_library.bzl", "proto_library")
load("@rules_android//android:rules.bzl", "android_library")

package(
    default_visibility = ["//visibility:public"],
)

proto_library(
    name = "lib_proto",
    srcs = [
        "lib.proto",
    ],
    deps = ["//simple_proto/external:external_proto"],
)

proto_library(
    name = "lib_proto_2024",
    srcs = [
        "lib_edition_2024.proto",
    ],
    option_deps = ["@protobuf//:java_features_proto"],
    deps = ["//simple_proto/external:external_proto"],
)

java_proto_library(
    name = "lib_java_proto",
    deps = [
        ":lib_proto",
        ":lib_proto_2024",
    ],
)

java_lite_proto_library(
    name = "lib_java_proto_lite",
    deps = [
        ":lib_proto",
        ":lib_proto_2024",
    ],
)

java_library(
    name = "lib",
    srcs = glob(["java/com/example/lib/**/*.java"]),
    deps = [
        ":lib_java_proto",
        "//simple_proto/external:external_java_proto",
    ],
)

android_library(
    name = "android_lib",
    srcs = glob(["java/com/example/android/**/*.java"]),
    deps = [
        ":lib_java_proto_lite",
        "//simple_proto/external:external_java_proto_lite",
    ],
)
