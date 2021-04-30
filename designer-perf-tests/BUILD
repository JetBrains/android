load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.designer-perf-tests",
    iml_files = ["intellij.android.designer-perf-tests.iml"],
    split_test_targets = {
        "perfgate": {
            "tags": ["perfgate"],
            "shard_count": 2,
            "test_filter": "com.android.tools.idea.rendering.PerfgateComposeTest",
        },
        "non-perfgate": {
        },
    },
    tags = [
        "no_mac",
        "no_test_windows",
    ],
    test_class = "com.android.tools.idea.rendering.PerfgateRenderTestSuite",
    test_data = [
        "//prebuilts/studio/jdk",
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android:test_deps.zip",
        "//tools/adt/idea/android/testData",
        "//tools/adt/idea/designer-perf-tests/testData",
        "//tools/base/build-system:gradle-distrib",
        "//tools/base/build-system:studio_repo",
        "//tools/base/third_party/kotlin:kotlin-m2repository.zip",
    ],
    test_resources = ["testData"],
    test_srcs = ["testSrc"],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
        "//tools/base/perf-logger:studio.perf-logger[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.extensions[module, test]",
        "//tools/adt/idea/compose-designer:intellij.android.compose-designer[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/.idea/libraries:layoutlib",
        "//tools/adt/idea/intellij.android.compose-common[module]",
        "//tools/adt/idea/designer:intellij.android.designer[module, test]",
    ],
)
