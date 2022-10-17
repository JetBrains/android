load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_repository")

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
        "//tools/adt/idea/android/annotations",
        "//tools/adt/idea/databinding/testData",
        "//tools/base/build-system:gradle-distrib",
        "//tools/base/build-system:android_gradle_plugin.zip",
        "//tools/base/build-system:android_gradle_plugin_runtime_dependencies",
        "//tools/data-binding:data_binding_runtime.zip",
        "//tools/data-binding:integration-test-projects",
    ],
    test_srcs = ["testSrc"],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:kotlin-test[test]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.extensions[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/databinding:intellij.android.databinding[module, test]",
        "//tools/analytics-library/tracker:analytics-tracker[module, test]",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing[module, test]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto[test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module, test]",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[module, test]",
        "//tools/adt/idea/project-system-gradle-models:intellij.android.projectSystem.gradle.models[module, test]",
        "//tools/adt/idea/project-system-gradle-upgrade:intellij.android.projectSystem.gradle.upgrade[module, test]",
        "//tools/adt/idea/.idea/libraries:asm-tools[test]",
    ],
)

maven_repository(
    # Dependencies needed for
    # tools/data-binding/integration-tests/TestApp
    name = "testapp_deps",
    # keep sorted
    artifacts = [
        "@maven//:androidx.constraintlayout.constraintlayout_1.1.3",
        "@maven//:androidx.fragment.fragment_1.0.0",
        "@maven//:androidx.lifecycle.lifecycle-livedata_2.4.0",
        "@maven//:androidx.lifecycle.lifecycle-runtime_2.4.0",
        "@maven//:com.android.support.support-v4_28.0.0",
        "@maven//:com.android.support.test.espresso.espresso-core_3.0.1",
        "@maven//:com.google.auto.value.auto-value_1.6.2",
        "@maven//:com.google.errorprone.error_prone_annotations_2.3.2",
        "@maven//:com.google.jimfs.jimfs_1.1",
        "@maven//:com.sun.activation.javax.activation_1.2.0",
        "@maven//:commons-lang.commons-lang_2.4",
        "@maven//:javax.annotation.jsr250-api_1.0",
        "@maven//:org.codehaus.mojo.animal-sniffer-annotations_1.17",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect_1.4.32",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk7_1.4.32",
        "@maven//:org.mockito.mockito-core_3.12.4",
        "@maven//:xmlpull.xmlpull_1.1.3.1",
    ],
)
