load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.device.tests",
    iml_files = ["intellij.android.device.tests.iml"],
    test_friends = ["//tools/adt/idea/intellij.android.device"],
    test_srcs = ["src"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/base/sdklib:studio.android.sdktools.sdklib[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/intellij.android.device[module, test]",
    ],
)
