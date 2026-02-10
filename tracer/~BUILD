load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.tracer",
    srcs = ["src"],
    iml_files = ["intellij.android.tracer.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/android-common:intellij.android.common",
        "@intellij//:intellij-sdk",
        "//tools/base/tracer:studio.android.sdktools.tracer",
    ],
)
