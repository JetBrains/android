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
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module]",
        "//tools/adt/idea/.idea/libraries:layoutlib",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.layoutlib-loader.tests",
    iml_files = ["intellij.android.layoutlib-loader.tests.iml"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle",
        "//tools/adt/idea/layoutlib-loader:intellij.android.layoutlib-loader[module, test]",
        "//tools/adt/idea/.idea/libraries:layoutlib[test]",
    ],
)
