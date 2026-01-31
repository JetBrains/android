#
# Description: Bazel aspect bundled with the Bazel IntelliJ plugin.
#

licenses(["notice"])

# Files needed at runtime for blaze-invoking integration tests
filegroup(
    name = "integration_test_files",
    srcs = [
        "java_classpath.bzl",
        ":BUILD.bazel",
    ],
    visibility = ["//visibility:public"],
)

# the aspect files that will be bundled with the final plugin zip
filegroup(
    name = "aspect_files",
    srcs = [
        ":aspect_files_only",
    ],
    visibility = ["//visibility:public"],
)

# Separate different targets as they are put into different directories
filegroup(
    name = "aspect_files_only",
    srcs = [
        "WORKSPACE",
        "build_compose_dependencies.bzl",
        "build_dependencies.bzl",
        "build_dependencies_android_deps.bzl",
        "build_dependencies_android_legacy_deps.bzl",
        "build_dependencies_android_rules_android_deps.bzl",
        "build_dependencies_cc_deps.bzl",
        "build_dependencies_deps.bzl",
        "build_dependencies_iml_module_java_deps.bzl",
        "build_dependencies_java_deps.bzl",
        "build_dependencies_java_proto_deps.bzl",
        "build_dependencies_kotlin_deps.bzl",
        "build_dependencies_kotlin_stub_deps.bzl",
        "java_classpath.bzl",
        ":BUILD.bazel",
    ],
    visibility = ["//visibility:public"],
)

# BUILD file bundled with the aspect must not override the BUILD file
# used for development. So we name it BUILD.aspect, and rename prior
# to bundling with the plugin.
genrule(
    name = "rename_files",
    srcs = ["BUILD.aspect"],
    outs = ["BUILD.bazel"],
    cmd = "cp $< $@",
)

_dev_aspect_path = package_name()
