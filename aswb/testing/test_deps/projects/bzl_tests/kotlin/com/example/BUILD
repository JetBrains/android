load("@bazel_skylib//:bzl_library.bzl", "bzl_library")
load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_binary", "kt_jvm_library")
load("//bzl_tests:test_fixture.bzl", "build_dependencies_deps_test_fixture")
load(":test.bzl", "test_suite")

package(
    default_visibility = ["//visibility:public"],
)

bzl_library(
    name = "test_bzl",
    srcs = ["test.bzl"],
    deps = [
        "//bzl_tests:check_utils_bzl",
        "//bzl_tests:test_fixture_bzl",
        "//bzl_tests/java/com/example:check_utils_bzl",
        "@rules_testing//lib:analysis_test_bzl",
    ],
)

kt_jvm_library(
    name = "foolib",
    srcs = ["Foo.kt"],
    deps = [":distant"],
)

kt_jvm_library(
    name = "distant",
    srcs = ["Distant.kt"],
)

# aswb:bazel-only-begin(kt_jvm_binary is available in bazel)
kt_jvm_binary(
    name = "foo",
    srcs = ["FooMain.kt"],
    main_class = "com.example.MyMainClass",
    deps = [":foolib"],
)
# aswb:bazel-only-end-and-replace-begin
# java_binary(
#    name = "foo",
#    main_class = "com.example.MyMainClass",
#    runtime_deps = [":foolib"],
# )
# aswb:replace-end

build_dependencies_deps_test_fixture(
    name = "kt_library_test_fixture",
    deps = [":foolib"],
)

build_dependencies_deps_test_fixture(
    name = "kt_binary_test_fixture",
    deps = [":foo"],
)

test_suite(name = "kt")
