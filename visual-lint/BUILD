load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.visual-lint",
    srcs = ["src"],
    iml_files = ["intellij.android.visual-lint.iml"],
    lint_baseline = "lint_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/rendering:intellij.android.rendering[module]",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module]",
        "//tools/adt/idea/.idea/libraries:layoutlib",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "@intellij//:intellij-sdk",
        "//tools/adt/idea/render-resources:intellij.android.render-resources[module]",
        "//tools/base/sdklib:studio.android.sdktools.sdklib[module]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
    ],
)
