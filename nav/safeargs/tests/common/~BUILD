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
        "noci:studio-win",
    ],
    test_class = "com.android.tools.idea.nav.safeargs.SafeArgsTestSuite",
    # keep sorted
    test_data = [
        "//tools/adt/idea/nav/safeargs/testData",
    ],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/nav/safeargs:intellij.android.nav.safeargs",
        "//tools/adt/idea/nav/safeargs/common:intellij.android.nav.safeargs.common",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/base/build-system/builder-model:studio.android.sdktools.builder-model",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing",
        "//tools/adt/idea/sdk-updates:intellij.android.sdkUpdates",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
    ],
    test_resources = ["testResources"],
    test_srcs = ["src"],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:com.intellij.gradle",
        "@intellij//:org.jetbrains.kotlin",
    ],
)
