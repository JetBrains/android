load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.layoutlib-legacy",
    srcs = ["src"],
    bundled_deps = ["//prebuilts/studio/layoutlib/data:layoutlib_legacy"],
    iml_files = ["intellij.android.layoutlib-legacy.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module]",
        "//prebuilts/studio/layoutlib/data:layoutlib_legacy",
    ],
)
