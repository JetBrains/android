load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.device.tests",
    iml_files = ["intellij.android.device.tests.iml"],
    kotlin_use_compose = True,
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/base/sdklib:studio.android.sdktools.sdklib",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/adt-ui-compose:intellij.android.adt.ui.compose",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/intellij.android.device",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/artwork-compose:intellij.android.artwork-compose",
        "@intellij//:test-framework",
    ],
    test_friends = ["//tools/adt/idea/intellij.android.device"],
    test_srcs = ["src"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
    ],
)
