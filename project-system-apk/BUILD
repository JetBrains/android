load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.projectSystem.apk",
    srcs = ["src"],
    iml_files = ["intellij.android.projectSystem.apk.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/base/apkparser/analyzer:studio.android.sdktools.analyzer",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/execution/common:intellij.android.execution.common",
    ],
)
