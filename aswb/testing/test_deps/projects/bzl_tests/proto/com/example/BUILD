load("@bazel_skylib//:bzl_library.bzl", "bzl_library")
load("@protobuf//bazel:java_lite_proto_library.bzl", "java_lite_proto_library")
load("@protobuf//bazel:java_proto_library.bzl", "java_proto_library")
load("@protobuf//bazel:proto_library.bzl", "proto_library")
load("//bzl_tests:test_fixture.bzl", "build_dependencies_deps_test_fixture")
load(":test.bzl", "test_suite")

package(
    default_visibility = ["//visibility:public"],
)

proto_library(
    name = "proto_lib",
    srcs = ["Foo.proto"],
)

java_proto_library(
    name = "java_proto",
    deps = [
        ":proto_lib",
    ],
)

java_lite_proto_library(
    name = "java_proto_lite",
    deps = [
        ":proto_lib",
    ],
)

bzl_library(
    name = "test_bzl",
    srcs = ["test.bzl"],
    deps = [
        ":check_utils_bzl",
        "//bzl_tests:check_utils_bzl",
        "//bzl_tests:test_fixture_bzl",
        "@rules_testing//lib:analysis_test_bzl",
    ],
)

bzl_library(
    name = "check_utils_bzl",
    srcs = ["check_utils.bzl"],
    deps = [
        "//bzl_tests:check_utils_bzl",
        "@rules_testing//lib/private:struct_subject_bzl",
    ],
)

build_dependencies_deps_test_fixture(
    name = "java_proto_library_test_fixture",
    deps = [":java_proto"],
)

build_dependencies_deps_test_fixture(
    name = "java_lite_proto_library_test_fixture",
    deps = [":java_proto_lite"],
)

test_suite(name = "proto")
