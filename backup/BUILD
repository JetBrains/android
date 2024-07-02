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
        "@intellij//:com.intellij.java",
        "//tools/base/adblib:studio.android.sdktools.adblib[module]",
        "//tools/base/backup:studio.android.sdktools.backup[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/base/environment-services:studio.intellij.android.environment-services[module]",
        "//tools/adt/idea/android-adb:intellij.android.adb[module]",
        "//tools/adt/idea/backup:intellij.android.backup.api[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/execution/common:intellij.android.execution.common[module]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.backup.tests",
    iml_files = ["intellij.android.backup.tests.iml"],
    test_class = "com.android.tools.idea.BackupTestSuite",
    test_friends = ["//tools/adt/idea/backup:intellij.android.backup"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/.idea/libraries:kotlin-test[test]",
        "@intellij//:intellij-sdk[test]",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/backup:intellij.android.backup[module, test]",
        "//tools/adt/idea/backup:intellij.android.backup.api[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/base/backup:studio.android.sdktools.backup[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
    ],
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
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/base/backup:studio.android.sdktools.backup[module]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
    ],
)
