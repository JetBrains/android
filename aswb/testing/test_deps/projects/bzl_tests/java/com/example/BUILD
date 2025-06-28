load("@bazel_skylib//:bzl_library.bzl", "bzl_library")
load("@rules_java//java:defs.bzl", "java_binary", "java_library")
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
        "@rules_testing//lib:analysis_test_bzl",
    ],
)

bzl_library(
    name = "check_utils_bzl",
    srcs = ["check_utils.bzl"],
)

java_library(
    name = "foolib",
    srcs = ["Foo.java"],
    deps = [":distant"],
)

java_library(
    name = "distant",
    srcs = ["Distant.java"],
)

java_binary(
    name = "foo",
    srcs = ["FooMain.java"],
    main_class = "com.example.MyMainClass",
    deps = [":foolib"],
)

build_dependencies_deps_test_fixture(
    name = "java_library_test_fixture",
    deps = [":foolib"],
)

build_dependencies_deps_test_fixture(
    name = "java_binary_test_fixture",
    deps = [":foo"],
)

test_suite(name = "java")
