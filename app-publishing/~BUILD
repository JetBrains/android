load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.app-publishing",
    srcs = ["src"],
    iml_files = ["intellij.android.app-publishing.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/adt/idea/.idea/libraries:google-api-client",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.app-publishing.tests",
    iml_files = ["intellij.android.app-publishing.tests.iml"],
    test_class = "com.android.tools.idea.publishing.AppPublishingTestSuite",
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/app-publishing:intellij.android.app-publishing",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/.idea/libraries:kotlin-test",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
    ],
    test_srcs = ["test"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/.idea/libraries:google-api-client",
    ],
)
