load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.layoutlib-native",
    srcs = ["src"],
    bundle_data = ["//prebuilts/studio/layoutlib:licenses"],
    iml_files = ["intellij.android.layoutlib-native.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module]",
        "//prebuilts/studio/layoutlib/data:layoutlib_native",
        "//tools/idea/platform/core-api:intellij.platform.core[module]",
        "//tools/idea/platform/bootstrap:intellij.platform.bootstrap[module]",
        "//tools/idea/platform/core-impl:intellij.platform.core.impl[module]",
    ],
)

# managed by go/iml_to_build [unb]
iml_module(
    name = "intellij.android.layoutlib-native",
    srcs = ["src"],
    bundle_data = ["//prebuilts/studio/layoutlib:licenses"],
    bundled_deps = ["//prebuilts/studio/layoutlib/data:unb.layoutlib_native"],
    iml_files = ["intellij.android.layoutlib-native.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    project = "unb",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module]",
        "//prebuilts/studio/layoutlib/data:unb.layoutlib_native",
    ],
)
