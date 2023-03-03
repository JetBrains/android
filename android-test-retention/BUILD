load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.testRetention",
    srcs = ["src"],
    iml_files = ["intellij.android.testRetention.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/deploy:intellij.android.deploy[module]",
        "//tools/adt/idea/streaming:intellij.android.streaming[module]",
        "//tools/adt/idea/.idea/libraries:emulator-proto",
        "//tools/adt/idea/.idea/libraries:studio-grpc",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//tools/adt/idea/execution/common:intellij.android.execution.common[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.testRetention.tests",
    iml_files = ["intellij.android.testRetention.tests.iml"],
    test_class = "com.android.tools.idea.retention.actions.TestRetentionTestSuite",
    test_data = ["//tools/adt/idea/artwork:device-art-resources"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/streaming:intellij.android.streaming[module, test]",
        "//tools/adt/idea/streaming:intellij.android.streaming.testUtil[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/android-test-retention:intellij.android.testRetention[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
    ],
)
