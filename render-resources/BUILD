load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.render-resources",
    srcs = ["src"],
    iml_files = ["intellij.android.render-resources.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/sdklib:studio.android.sdktools.sdklib[module]",
        "//tools/adt/idea/layoutlib-loader:intellij.android.layoutlib-loader[module]",
    ],
)

# Build target to verify that render-resources builds for tools/base
kotlin_library(
    name = "tools.render-resources",
    srcs = glob([
        "src/**/*.java",
        "src/**/*.kt",
    ]),
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/tools/common/lint-psi/intellij-core",
        "//tools/adt/idea/layoutlib-loader:tools.layoutlib-loader",
        "//tools/base/common:tools.common",
        "//tools/base/sdklib:tools.sdklib",
    ],
)
