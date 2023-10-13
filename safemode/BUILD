load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.safemode",
    iml_files = ["intellij.android.safemode.iml"],
    visibility = ["//visibility:public"],
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
