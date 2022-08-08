load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.threading-checker",
    srcs = ["src"],
    iml_files = ["intellij.android.threading-checker.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/threading-agent-callback:studio.android.sdktools.threading-agent-callback[module]",
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
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:kotlin-test[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/base/threading-agent-callback:studio.android.sdktools.threading-agent-callback[module]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/threading-checker:intellij.android.threading-checker[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
    ],
)
