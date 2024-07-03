load("//tools/adt/idea/studio:studio.bzl", "iml_studio_test")
load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.safemode",
    srcs = ["src"],
    iml_files = ["intellij.android.safemode.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
    ],
)

py_binary(
    name = "generate_safe_mode_scripts",
    srcs = ["script-generation/generate_safe_mode_scripts.py"],
    data = [
        "//tools/adt/idea/studio:android-studio.linux.zip",
        "//tools/adt/idea/studio:android-studio.mac.zip",
        "//tools/adt/idea/studio:android-studio.win.zip",
    ],
)

py_test(
    name = "test_safe_mode",
    size = "small",
    srcs = ["tests/test_safe_mode.py"],
    data = [
        "//tools/adt/idea/studio:android-studio.linux.zip",
        "//tools/adt/idea/studio:android-studio.mac.zip",
        "//tools/adt/idea/studio:android-studio.win.zip",
    ],
)

exports_files(
    ["linux/studio_safe.sh"],
    visibility = ["//visibility:public"],
)

exports_files(
    ["mac/studio_safe.sh"],
    visibility = ["//visibility:public"],
)

exports_files(
    ["win/studio_safe.bat"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.safemode.integration.tests",
    enable_tests = False,
    iml_files = ["intellij.android.safemode.integration.tests.iml"],
    test_srcs = ["integration/testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/as-driver:as-driver.utils[module, test]",
        "//tools/base/common:studio.android.sdktools.common[module, test]",
        "@intellij//:intellij-sdk[test]",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/.idea/libraries:truth[test]",
    ],
)

iml_studio_test(
    name = "StartUpSafeModeTest",
    module = ":intellij.android.safemode.integration.tests",
    tags_linux = ["block-network"],
    tags_windows = ["block-network"],
    test_class = "StartUpSafeModeTest",
)
