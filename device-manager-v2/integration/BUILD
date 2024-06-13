load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/adt/idea/studio:studio.bzl", "iml_studio_test")

# managed by go/iml_to_build
iml_module(
    name = "device-manager-v2.integration",
    enable_tests = False,
    iml_files = ["device-manager-v2.integration.iml"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/tools/common/m2:junit-4.12[test]",
        "//tools/adt/idea/as-driver:as-driver.utils[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "@intellij//:intellij-sdk[test]",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/.idea/libraries:truth[test]",
    ],
)

iml_studio_test(
    name = "DeviceManagerTest",
    module = ":device-manager-v2.integration",
    tags_linux = ["block-network"],
    tags_windows = ["block-network"],
    test_class = "com.android.tools.idea.devicemanagerv2.DeviceManagerTest",
)
