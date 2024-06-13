load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.nav.safeargs.tests.common",
    iml_files = ["intellij.android.nav.safeargs.tests.common.iml"],
    split_test_targets = {
        "gradle": {
            "tags": ["cpu:3"],
            "shard_count": 2,
            "test_filter": ".gradle",
            "data": [
                # keep sorted
                "//prebuilts/studio/sdk:build-tools/latest",
                "//prebuilts/studio/sdk:platforms/latest",
                "//tools/adt/idea/nav/safeargs/tests:testdeps_repo",
                "//tools/base/build-system:android_gradle_plugin.zip",
                "//tools/base/build-system:android_gradle_plugin_runtime_dependencies",
                "//tools/base/build-system:gradle-distrib",
                "//tools/base/build-system/integration-test:kotlin_gradle_plugin_prebuilts",
            ],
        },
        "non-gradle": {},
    },
    tags = [
        "no_test_windows",
    ],
    test_class = "com.android.tools.idea.nav.safeargs.SafeArgsTestSuite",
    # keep sorted
    test_data = [
        "//tools/adt/idea/nav/safeargs/testData",
    ],
    test_srcs = ["src"],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:com.intellij.gradle",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/nav/safeargs:intellij.android.nav.safeargs[module, test]",
        "//tools/adt/idea/nav/safeargs/common:intellij.android.nav.safeargs.common[module, test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/base/build-system/builder-model:studio.android.sdktools.builder-model[module, test]",
        "//tools/analytics-library/tracker:analytics-tracker[module, test]",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing[module, test]",
        "//tools/adt/idea/sdk-updates:intellij.android.sdkUpdates[module, test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module, test]",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[module, test]",
        "//tools/adt/idea/render-resources:intellij.android.render-resources[module, test]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module, test]",
    ],
)
