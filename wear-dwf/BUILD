load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.wear-dwf",
    srcs = ["src"],
    iml_files = ["intellij.android.wear-dwf.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/base/wear-wff-schema:studio.intellij.android.wear-wff-schema",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.wear-dwf.tests",
    iml_files = ["intellij.android.wear-dwf.tests.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "//tools/adt/idea/wear-dwf:intellij.android.wear-dwf",
    ],
)
