load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.jps.model",
    srcs = ["src"],
    iml_files = ["intellij.android.jps.model.iml"],
    jvm_target = "8",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/common:studio.android.sdktools.common[module]",
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
    ],
)
