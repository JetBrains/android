load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.preview-fast-compile",
    srcs = ["src"],
    iml_files = ["intellij.android.preview-fast-compile.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "//tools/base/environment-services:studio.intellij.android.environment-services[module]",
    ],
)

# Build target to verify that preview-fast-compile builds for tools/base
kotlin_library(
    name = "tools.preview-fast-compile",
    srcs = glob([
        "src/**/*.java",
        "src/**/*.kt",
    ]),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/environment-services:tools.environment-services",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core",
    ],
)
