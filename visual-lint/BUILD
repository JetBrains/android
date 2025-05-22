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
        "//tools/adt/idea/rendering:intellij.android.rendering",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api",
        "//tools/adt/idea/.idea/libraries:layoutlib",
        "//tools/base/common:studio.android.sdktools.common",
        "@intellij//:intellij-sdk",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
        "//tools/base/sdklib:studio.android.sdktools.sdklib",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common",
    ],
)
