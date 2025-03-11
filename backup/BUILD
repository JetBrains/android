load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.backup",
    # do not sort: must match IML order
    srcs = ["src"],
    iml_files = ["intellij.android.backup.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "@intellij//:com.intellij.java",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/base/adblib:studio.android.sdktools.adblib",
        "//tools/base/backup:studio.android.sdktools.backup",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/base/environment-services:studio.intellij.android.environment-services",
        "//tools/adt/idea/android-adb:intellij.android.adb",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/backup:intellij.android.backup.api",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/execution/common:intellij.android.execution.common",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/streaming:intellij.android.streaming",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.backup.tests",
    iml_files = ["intellij.android.backup.tests.iml"],
    test_class = "com.android.tools.idea.BackupTestSuite",
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/.idea/libraries:kotlin-test",
        "@intellij//:intellij-sdk",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/backup:intellij.android.backup",
        "//tools/adt/idea/backup:intellij.android.backup.api",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/execution/common:intellij.android.execution.common",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/base/backup:studio.android.sdktools.backup",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/base/flags:studio.android.sdktools.flags",
    ],
    test_friends = ["//tools/adt/idea/backup:intellij.android.backup"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = ["@intellij//:com.intellij.java"],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.backup.api",
    srcs = ["api/src"],
    iml_files = ["intellij.android.backup.api.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/base/annotations:studio.android.sdktools.android-annotations",
        "//tools/base/backup:studio.android.sdktools.backup",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
    ],
)
