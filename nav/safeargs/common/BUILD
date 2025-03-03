load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.nav.safeargs.common",
    srcs = ["src"],
    iml_files = ["intellij.android.nav.safeargs.common.iml"],
    lint_baseline = "lint_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/analytics",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
    ],
)
