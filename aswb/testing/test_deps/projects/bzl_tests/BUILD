load("@bazel_skylib//:bzl_library.bzl", "bzl_library")
load("@bazel_skylib//rules:build_test.bzl", "build_test")

package(
    default_visibility = ["//visibility:private"],
)

bzl_library(
    name = "test_fixture_bzl",
    srcs = ["test_fixture.bzl"],
)

bzl_library(
    name = "check_utils_bzl",
    srcs = ["check_utils.bzl"],
    deps = ["@bazel_skylib//lib:types_bzl"],
)

build_test(
    name = "bzl_tests",
    targets = [":BUILD"],  # Building a source file succeeds.
)

build_test(
    name = "bzl_tests_failing",
    targets = [":target"],
)

genrule(
    name = "failing_target",
    outs = ["out.txt"],
    cmd = "echo 'This is error' >&2 ; exit 1",
)

exports_files(
    [
        "test_fixture.bzl",
        "check_utils.bzl",
    ],
    visibility = ["//visibility:public"],
)
