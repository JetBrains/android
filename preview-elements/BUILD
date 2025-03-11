load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.preview-elements",
    srcs = ["src"],
    iml_files = ["intellij.android.preview-elements.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
        "//tools/adt/idea/rendering:intellij.android.rendering",
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/base/sdklib:studio.android.sdktools.sdklib",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common",
        "//tools/base/environment-services:studio.intellij.android.environment-services",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.preview-elements.tests",
    iml_files = ["intellij.android.preview-elements.tests.iml"],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "@intellij//:intellij-sdk",
        "//tools/adt/idea/.idea/libraries:kotlin-test",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/preview-elements:intellij.android.preview-elements",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api",
        "//tools/base/sdklib:studio.android.sdktools.sdklib",
        "//tools/base/environment-services:studio.intellij.android.environment-services",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common",
        "//tools/adt/idea/layoutlib-loader:intellij.android.layoutlib-loader",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = ["@intellij//:com.intellij.java"],
)

# Build target to verify that preview-elements builds for tools/base
kotlin_library(
    name = "tools.preview-elements",
    srcs = glob([
        "src/**/*.java",
        "src/**/*.kt",
    ]),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/adt/idea/render-resources:tools.render-resources",
        "//tools/adt/idea/rendering:tools.rendering",
        "//tools/base/common:tools.common",
        "//tools/base/environment-services:tools.environment-services",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/resource-repository:tools.resource-repository",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
    ],
)
