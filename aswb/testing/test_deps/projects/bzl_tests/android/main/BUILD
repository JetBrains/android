load("@bazel_skylib//:bzl_library.bzl", "bzl_library")
load("@rules_android//android:rules.bzl", "android_binary")
load("//bzl_tests:test_fixture.bzl", "build_dependencies_deps_test_fixture")
load(":test.bzl", "test_suite")

package(
    default_visibility = ["//visibility:public"],
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
    deps = ["//bzl_tests:check_utils_bzl"],
)

android_binary(
    name = "basic_app",
    manifest = "AndroidManifest.xml",
    deps = ["//bzl_tests/android/main/java/com/basicapp:basic_lib"],
)

build_dependencies_deps_test_fixture(
    name = "android_library_test_fixture",
    deps = ["//bzl_tests/android/main/java/com/basicapp:basic_lib"],
)

build_dependencies_deps_test_fixture(
    name = "android_binary_test_fixture",
    deps = [":basic_app"],
)

test_suite(name = "android")
