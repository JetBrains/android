load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.settings-sync",
    srcs = ["src"],
    iml_files = ["intellij.android.settings-sync.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/.idea/libraries:google-api-client",
        "//prebuilts/tools/common/m2:google-api-services-drive-v3-rev20241027-2.0.0",
        "@intellij//:intellij-sdk",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
    ],
)
