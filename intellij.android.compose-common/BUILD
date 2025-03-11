load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.compose-common",
    srcs = ["src"],
    iml_files = ["intellij.android.compose-common.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.compose-common.tests",
    iml_files = ["intellij.android.compose-common.tests.iml"],
    test_class = "com.android.tools.compose.ComposeCommonTestSuite",
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/adt/idea/intellij.android.compose-common",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/android-plugin:intellij.android.plugin",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "@intellij//:intellij-sdk",
        "@intellij//:org.jetbrains.kotlin",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/.idea/libraries:truth",
    ],
)
