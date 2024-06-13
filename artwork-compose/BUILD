load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.artwork-compose",
    srcs = ["src"],
    iml_files = ["intellij.android.artwork-compose.iml"],
    kotlin_use_compose = True,
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/adt-ui-compose:intellij.android.adt.ui.compose[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
    ],
)
