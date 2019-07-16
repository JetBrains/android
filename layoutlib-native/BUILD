load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.layoutlib-native",
    srcs = ["src"],
    bundle_data = ["//prebuilts/studio/layoutlib:licenses"],
    iml_files = ["intellij.android.layoutlib-native.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module]",
        "//prebuilts/studio/layoutlib:data/layoutlib_native",
    ],
)
