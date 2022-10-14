load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.device-explorer-files",
    srcs = ["src"],
    iml_files = ["intellij.android.device-explorer-files.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/adt/idea/android-adb:intellij.android.adb[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/.idea/libraries:kotlinx-coroutines-guava",
        "//tools/base/sdklib:studio.android.sdktools.sdklib[module]",
        "//tools/base/adblib:studio.android.sdktools.adblib[module]",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner[module]",
    ],
)
