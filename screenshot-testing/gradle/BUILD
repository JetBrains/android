load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.screenshot-test.gradle",
    srcs = ["src"],
    iml_files = ["intellij.android.screenshot-test.gradle.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
        "//tools/adt/idea/screenshot-testing:intellij.android.screenshot-test[module]",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
    ],
)
