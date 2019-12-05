load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.mlkit",
    srcs = ["src"],
    iml_files = ["intellij.android.mlkit.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = ["//tools/idea/platform/core-api:intellij.platform.core[module]"],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.mlkit.tests",
    iml_files = ["intellij.android.mlkit.tests.iml"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/mlkit:intellij.android.mlkit[module, test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
    ],
)
