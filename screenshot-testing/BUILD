load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.screenshot-test",
    srcs = ["src"],
    iml_files = ["intellij.android.screenshot-test.iml"],
    kotlin_use_compose = True,
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/adt/idea/adt-ui-compose:intellij.android.adt.ui.compose[module]",
    ],
)
