load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.tracer",
    srcs = ["src"],
    iml_files = ["intellij.android.tracer.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    exports = ["//prebuilts/tools/common/m2:androidx-tracing-desktop"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/adt-ui-compose:intellij.android.adt.ui.compose",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/android-common:intellij.android.common",
        "@intellij//:intellij-sdk",
        "//prebuilts/tools/common/m2:androidx-tracing-desktop",
    ],
)
