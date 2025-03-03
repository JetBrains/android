load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "analytics",
    srcs = ["src"],
    iml_files = ["analytics.iml"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/.idea/libraries:HdrHistogram",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/adt/idea/jps-model:intellij.android.jps.model",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib",
        "//tools/analytics-library/shared:analytics-shared",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common",
        "//tools/base/adblib:studio.android.sdktools.adblib",
    ],
)
