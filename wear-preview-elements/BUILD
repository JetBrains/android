load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.wear-preview-elements",
    srcs = ["src"],
    iml_files = ["intellij.android.wear-preview-elements.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
        "//tools/adt/idea/preview-elements:intellij.android.preview-elements[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.wear-preview-elements.tests",
    iml_files = ["intellij.android.wear-preview-elements.tests.iml"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "@intellij//:intellij-sdk[test]",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/.idea/libraries:kotlin-test[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/wear-preview-elements:intellij.android.wear-preview-elements[module, test]",
        "//tools/adt/idea/preview-elements:intellij.android.preview-elements[module, test]",
    ],
)

# Build target to verify that wear-preview-elements builds for tools/base
kotlin_library(
    name = "tools.wear-preview-elements",
    srcs = glob([
        "src/**/*.java",
        "src/**/*.kt",
    ]),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/adt/idea/preview-elements:tools.preview-elements",
        "//tools/base/sdk-common:tools.sdk-common",
    ],
)
