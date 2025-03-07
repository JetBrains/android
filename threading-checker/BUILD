load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.threading-checker",
    srcs = ["src"],
    iml_files = ["intellij.android.threading-checker.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/base/threading-agent-callback:studio.android.sdktools.threading-agent-callback",
        "//tools/base/annotations:studio.android.sdktools.android-annotations",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.threading-checker.tests",
    iml_files = ["intellij.android.threading-checker.tests.iml"],
    test_class = "com.android.tools.idea.instrumentation.threading.ThreadingCheckerTestSuite",
    test_friends = ["//tools/adt/idea/threading-checker:intellij.android.threading-checker"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/base/threading-agent-callback:studio.android.sdktools.threading-agent-callback",
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "//tools/adt/idea/.idea/libraries:kotlin-test[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[test]",
        "//tools/adt/idea/threading-checker:intellij.android.threading-checker[test]",
        "//tools/adt/idea/android-common:intellij.android.common[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[test]",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing[test]",
        "//tools/analytics-library/tracker:analytics-tracker[test]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto[test]",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin[test]",
    ],
)
