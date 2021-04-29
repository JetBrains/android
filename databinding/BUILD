load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.databinding",
    srcs = ["src"],
    iml_files = ["intellij.android.databinding.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    test_resources = ["testData"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.databinding.tests",
    iml_files = ["intellij.android.databinding.tests.iml"],
    split_test_targets = {
        "gradle": {
            "tags": ["cpu:3"],
            "shard_count": 2,
            "test_filter": ".gradle",
        },
        "non-gradle": {},
    },
    tags = [
        "no_test_mac",
        "no_test_windows",
    ],
    test_class = "com.android.tools.idea.databinding.AndroidDataBindingTestSuite",
    test_data = [
        ":testapp_deps",
        "//prebuilts/studio/jdk",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android:test_deps.zip",
        "//tools/adt/idea/android/annotations",
        "//tools/adt/idea/databinding/testData",
        "//tools/base/build-system:gradle-distrib",
        "//tools/base/build-system:studio_repo.zip",
        "//tools/data-binding:data_binding_runtime.zip",
        "//tools/data-binding:integration-test-projects",
    ],
    test_srcs = ["testSrc"],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.extensions[module, test]",
        "//tools/adt/idea/resources-base:intellij.android.resources-base[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/databinding:intellij.android.databinding[module, test]",
        "//tools/analytics-library/tracker:analytics-tracker[module, test]",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing[module, test]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto[test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module, test]",
        "//tools/adt/idea/project-system-gradle-models:intellij.android.projectSystem.gradle.models[module]",
    ],
)

load("//tools/base/bazel:maven.bzl", "maven_repo")

maven_repo(
    # Dependencies needed for
    # tools/data-binding/integration-tests/TestApp
    name = "testapp_deps",
    # keep sorted
    artifacts = [
        "//prebuilts/tools/common/m2/repository/com/android/support/test/espresso/espresso-core/3.0.1:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/test/espresso/espresso-idling-resource/3.0.1:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/test/rules/1.0.1:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/test/runner/1.0.1:aar",
        "//prebuilts/tools/common/m2/repository/com/linkedin/dexmaker/dexmaker-mockito/2.19.0:jar",
        "//prebuilts/tools/common/m2/repository/com/linkedin/dexmaker/dexmaker/2.19.0:jar",
        "//prebuilts/tools/common/m2/repository/javax/annotation/jsr250-api/1.0:jar",
        "//prebuilts/tools/common/m2/repository/net/bytebuddy/byte-buddy-agent/1.9.10:jar",
        "//prebuilts/tools/common/m2/repository/net/bytebuddy/byte-buddy/1.9.10:jar",
        "//prebuilts/tools/common/m2/repository/org/mockito/mockito-core/3.0.0:jar",
        "//prebuilts/tools/common/m2/repository/org/objenesis/objenesis/2.6:jar",
    ],
    use_zip = False,
)
