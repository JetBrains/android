load("@rules_android//android:rules.bzl", "android_binary")
load("//bzl_tests:test_fixture.bzl", "build_dependencies_deps_test_fixture")
load(":test.bzl", "test_suite")

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
