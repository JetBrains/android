load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_binary", "kt_jvm_library")
load("//bzl_tests:test_fixture.bzl", "build_dependencies_deps_test_fixture")
load(":test.bzl", "test_suite")

package(
    default_visibility = ["//visibility:public"],
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

kt_jvm_binary(
    name = "foo",
    srcs = ["FooMain.kt"],
    main_class = "com.example.MyMainClass",
    deps = [":foolib"],
)

build_dependencies_deps_test_fixture(
    name = "kt_library_test_fixture",
    deps = [":foolib"],
)

build_dependencies_deps_test_fixture(
    name = "kt_binary_test_fixture",
    deps = [":foo"],
)

test_suite(name = "kt")
