load("@rules_java//java:defs.bzl", "java_binary", "java_library")
load("//bzl_tests:test_fixture.bzl", "build_dependencies_deps_test_fixture")
load(":test.bzl", "test_suite")

package(
    default_visibility = ["//visibility:public"],
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
