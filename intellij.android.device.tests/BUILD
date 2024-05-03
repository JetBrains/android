load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.device.tests",
    iml_files = ["intellij.android.device.tests.iml"],
    kotlin_use_compose = True,
    test_friends = ["//tools/adt/idea/intellij.android.device"],
    test_srcs = ["src"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/base/sdklib:studio.android.sdktools.sdklib[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-ui-compose:intellij.android.adt.ui.compose[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/intellij.android.device[module, test]",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/artwork-compose:intellij.android.artwork-compose[module, test]",
        "@intellij//:test-framework[test]",
    ],
)
