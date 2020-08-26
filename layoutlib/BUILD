load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.layoutlib",
    srcs = ["src"],
    iml_files = ["intellij.android.layoutlib.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module]",
        "//prebuilts/studio/layoutlib/data:layoutlib",
    ],
)

# managed by go/iml_to_build [unb]
iml_module(
    name = "intellij.android.layoutlib",
    srcs = ["src"],
    bundled_deps = ["//prebuilts/studio/layoutlib/data:unb.layoutlib"],
    iml_files = ["intellij.android.layoutlib.iml"],
    project = "unb",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module]",
        "//prebuilts/studio/layoutlib/data:unb.layoutlib",
    ],
)
