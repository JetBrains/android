load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.layoutlib",
    srcs = ["src"],
    iml_files = ["intellij.android.layoutlib.iml"],
    visibility = ["//visibility:public"],
    exports = ["//tools/adt/idea/.idea/libraries:asm-tools"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:asm-tools",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module]",
        "//tools/adt/idea/layoutlib-loader:intellij.android.layoutlib-loader[module]",
        "//tools/adt/idea/.idea/libraries:layoutlib",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.layoutlib.tests",
    iml_files = ["intellij.android.layoutlib.tests.iml"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:kotlin-test[test]",
        "//tools/adt/idea/layoutlib:intellij.android.layoutlib[module, test]",
        "//tools/adt/idea/.idea/libraries:layoutlib[test]",
    ],
)
