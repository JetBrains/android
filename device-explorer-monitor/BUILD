load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.device-explorer-monitor",
    srcs = ["src"],
    iml_files = ["intellij.android.device-explorer-monitor.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/android-adb:intellij.android.adb[module]",
        "//tools/base/adblib:studio.android.sdktools.adblib[module]",
        "//tools/base/sdklib:studio.android.sdktools.sdklib[module]",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//prebuilts/studio/intellij-sdk:studio-sdk",
    ],
)
