load("//tools/base/bazel:bazel.bzl", "iml_module", "iml_test")

# managed by go/iml_to_build
iml_module(
    name = "device-manager-v2.integration",
    enable_tests = False,
    iml_files = ["device-manager-v2.integration.iml"],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//prebuilts/tools/common/m2:junit-4.12",
        "//tools/adt/idea/as-driver:as-driver.utils",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "@intellij//:intellij-sdk",
        "//tools/adt/idea/.idea/libraries:truth",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = ["@intellij//:com.intellij.java"],
)

iml_test(
    name = "DeviceManagerTest",
    module = ":device-manager-v2.integration",
    tags_linux = ["block-network"],
    tags_windows = ["block-network"],
    test_class = "com.android.tools.idea.devicemanagerv2.DeviceManagerTest",
)
