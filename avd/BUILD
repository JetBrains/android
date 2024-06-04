load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.avd",
    srcs = ["src"],
    iml_files = ["intellij.android.avd.iml"],
    kotlin_use_compose = True,
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/base/sdklib:studio.android.sdktools.sdklib[module]",
        "//tools/adt/idea/adt-ui-compose:intellij.android.adt.ui.compose[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/intellij.android.device[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner[module]",
        "//tools/adt/idea/artwork-compose:intellij.android.artwork-compose[module]",
        "//tools/adt/idea/wizard:intellij.android.wizard[module]",
        "//tools/adt/idea/wizard-model:intellij.android.wizard.model[module]",
        "@intellij//:test-framework[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
    ],
)
