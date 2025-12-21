#
# Description: Bazel aspect bundled with the Bazel IntelliJ plugin.
#

licenses(["notice"])

# Files needed at runtime for blaze-invoking integration tests
filegroup(
    name = "integration_test_files",
    srcs = [
        "artifacts.bzl",
        "java_classpath.bzl",
        "make_variables.bzl",
        ":BUILD.bazel",
        "//tools/adt/idea/aswb/aspect/tools:CreateAar",
        "//tools/adt/idea/aswb/aspect/tools:JarFilter_deploy.jar",
        "//tools/adt/idea/aswb/aspect/tools:PackageParser_deploy.jar",
    ],
    visibility = ["//visibility:public"],
)

# the aspect files that will be bundled with the final plugin zip
filegroup(
    name = "aspect_files",
    srcs = [
        ":aspect_files_only",
        ":aspect_tools",
    ],
    visibility = ["//visibility:public"],
)

# Separate different targets as they are put into different directories
filegroup(
    name = "aspect_files_only",
    srcs = [
        "WORKSPACE",
        "artifacts.bzl",
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
        "java_classpath.bzl",
        "make_variables.bzl",
        ":BUILD.bazel",
    ],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "aspect_tools",
    srcs = [
        "//tools/adt/idea/aswb/aspect/tools:CreateAar",
        "//tools/adt/idea/aswb/aspect/tools:JarFilter_deploy.jar",
        "//tools/adt/idea/aswb/aspect/tools:PackageParser_deploy.jar",
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
