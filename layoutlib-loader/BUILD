load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.layoutlib-loader",
    srcs = ["src"],
    iml_files = ["intellij.android.layoutlib-loader.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module]",
    ],
)

# Build target to verify that layoutlib-loader builds for tools/base
java_library(
    name = "tools.layoutlib-loader",
    srcs = glob(
        ["src/**/*.java"],
    ),
    resources = glob(["resources/**"]),
    deps = [
        "//prebuilts/tools/common/lint-psi/intellij-core",
        "//tools/base/common:tools.common",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "@maven//:com.google.guava.guava",
    ],
)
