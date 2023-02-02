load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.device",
    srcs = ["src"],
    iml_files = ["intellij.android.device.iml"],
    lint_baseline = "lint_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/sdklib:studio.android.sdktools.sdklib[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/wizard:intellij.android.wizard[module]",
        "//tools/adt/idea/wizard-model:intellij.android.wizard.model[module]",
    ],
)
