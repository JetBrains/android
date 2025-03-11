load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.navigator",
    srcs = ["src"],
    iml_files = ["intellij.android.navigator.iml"],
    lint_baseline = "lint_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:com.intellij.gradle",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/apkanalyzer:intellij.android.apkanalyzer",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/android-lang:intellij.android.lang",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle",
        "//tools/adt/idea/smali:intellij.android.smali",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/.idea/libraries:google-dexlib2",
        "//tools/adt/idea/analytics",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.navigator.testutils",
    iml_files = ["intellij.android.navigator.testutils.iml"],
    test_deps = ["//tools/adt/idea/.idea/libraries:junit4"],
    test_srcs = ["testUtils"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/android-navigator:intellij.android.navigator",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/adt/idea/.idea/libraries:truth",
    ],
)
