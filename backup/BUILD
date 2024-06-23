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
        "//tools/adt/idea/android-common:intellij.android.common[module]",
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
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/backup:intellij.android.backup[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
    ],
)
