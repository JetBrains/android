load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_repository")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.databinding",
    srcs = ["src"],
    iml_files = ["intellij.android.databinding.iml"],
    test_resources = ["testData"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
        "//tools/adt/idea/analytics",
        "//tools/base/flags:studio.android.sdktools.flags",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.databinding.tests",
    generate_k1_tests = True,
    iml_files = ["intellij.android.databinding.tests.iml"],
    split_test_targets = {
        "gradle": {
            "tags": ["cpu:3"],
            "shard_count": 4,
            "test_filter": ".gradle",
        },
        "non-gradle": {},
    },
    tags = [
        "noci:studio-win",
    ],
    test_class = "com.android.tools.idea.databinding.AndroidDataBindingTestSuite",
    test_data = [
        ":testapp_deps",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platforms/android-34",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/annotations",
        "//tools/adt/idea/android/testData",
        "//tools/adt/idea/databinding/testData",
        "//tools/base/build-system:android_gradle_plugin.zip",
        "//tools/base/build-system:android_gradle_plugin_runtime_dependencies",
        "//tools/base/build-system:gradle-distrib",
        "//tools/data-binding:data_binding_runtime.zip",
        "//tools/data-binding:integration-test-projects",
    ],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/.idea/libraries:kotlin-test",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/databinding:intellij.android.databinding",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle",
        "//tools/adt/idea/project-system-gradle-models:intellij.android.projectSystem.gradle.models",
        "//tools/adt/idea/project-system-gradle-upgrade:intellij.android.projectSystem.gradle.upgrade",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
        "//tools/adt/idea/.idea/libraries:asm-tools",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/databinding/gradle:intellij.android.databinding.gradle",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
    ],
    test_srcs = ["testSrc"],
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

maven_repository(
    # Dependencies needed for
    # tools/data-binding/integration-tests/TestApp
    name = "testapp_deps",
    # keep sorted
    artifacts = [
        "@maven//:androidx.constraintlayout.constraintlayout_1.1.3",
        "@maven//:androidx.databinding.viewbinding_7.4.1",
        "@maven//:androidx.fragment.fragment_1.0.0",
        "@maven//:androidx.lifecycle.lifecycle-common_2.6.1",
        "@maven//:androidx.lifecycle.lifecycle-livedata-ktx_2.6.1",
        "@maven//:androidx.lifecycle.lifecycle-livedata_2.6.1",
        "@maven//:androidx.lifecycle.lifecycle-process_2.6.1",
        "@maven//:androidx.lifecycle.lifecycle-runtime-ktx_2.6.1",
        "@maven//:androidx.lifecycle.lifecycle-runtime_2.6.1",
        "@maven//:androidx.lifecycle.lifecycle-service_2.6.1",
        "@maven//:androidx.lifecycle.lifecycle-viewmodel-savedstate_2.6.1",
        "@maven//:androidx.lifecycle.lifecycle-viewmodel_2.6.1",
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
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core_1.6.4",
        "@maven//:org.mockito.mockito-core_5.12.0",
        "@maven//:xmlpull.xmlpull_1.1.3.1",
    ],
)
