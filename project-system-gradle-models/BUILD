load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.projectSystem.gradle.models",
    srcs = ["src"],
    iml_files = ["intellij.android.projectSystem.gradle.models.iml"],
    visibility = ["//visibility:public"],
    deps = ["//prebuilts/studio/intellij-sdk:studio-sdk"],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.projectSystem.gradle.models.tests",
    iml_files = ["intellij.android.projectSystem.gradle.models.tests.iml"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module]",
    ],
)
