load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_repository")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.lang-databinding",
    # do not sort: must match IML order
    srcs = [
        "src",
        "gen",
    ],
    iml_files = ["intellij.android.lang-databinding.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/data-binding:studio.compilerCommon[module]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.lang-databinding.tests",
    iml_files = ["intellij.android.lang-databinding.tests.iml"],
    tags = [
        "no_test_mac",  # b/113099009
        "no_test_windows",
    ],
    test_class = "com.android.tools.idea.lang.databinding.LangDataBindingTestSuite",
    test_data = [
        ":test_deps",
        "//prebuilts/studio/jdk",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/annotations",
        "//tools/adt/idea/android-lang-databinding/testData",
        "//tools/base/build-system/integration-test:kotlin_gradle_plugin_prebuilts",
        "//tools/base/build-system:android_gradle_plugin.zip",
        "//tools/base/build-system:android_gradle_plugin_runtime_dependencies",
        "//tools/base/build-system:gradle-distrib",
        "//tools/data-binding:data_binding_runtime.zip",
    ],
    test_shard_count = 2,
    test_srcs = ["testSrc"],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    runtime_deps = ["//tools/adt/idea/android-kotlin:intellij.android.kotlin.extensions"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle",
        "//tools/adt/idea/android-lang-databinding:intellij.android.lang-databinding[module]",
        "//tools/adt/idea/databinding:intellij.android.databinding[module]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[module]",
    ],
)

maven_repository(
    name = "test_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:androidx.activity.activity_1.3.1",
        "@maven//:androidx.annotation.annotation_1.2.0-rc01",
        "@maven//:androidx.appcompat.appcompat_1.3.0",
        "@maven//:androidx.constraintlayout.constraintlayout_2.1.3",
        "@maven//:androidx.core.core_1.5.0-beta01",
        "@maven//:androidx.core.core_1.5.0-rc02",
        "@maven//:androidx.lifecycle.lifecycle-common_2.4.0",
        "@maven//:androidx.lifecycle.lifecycle-extensions_2.2.0",
        "@maven//:androidx.lifecycle.lifecycle-livedata-ktx_2.4.0",
        "@maven//:androidx.lifecycle.lifecycle-livedata_2.4.0",
        "@maven//:androidx.lifecycle.lifecycle-process_2.4.0",
        "@maven//:androidx.lifecycle.lifecycle-runtime-ktx_2.4.0",
        "@maven//:androidx.lifecycle.lifecycle-runtime_2.4.0",
        "@maven//:androidx.lifecycle.lifecycle-service_2.4.0",
        "@maven//:androidx.lifecycle.lifecycle-viewmodel_2.4.0",
        "@maven//:androidx.savedstate.savedstate_1.1.0-rc01",
        "@maven//:com.android.support.support-v4_28.0.0",
        "@maven//:com.google.auto.value.auto-value_1.6.2",
        "@maven//:com.google.errorprone.error_prone_annotations_2.3.2",
        "@maven//:com.google.jimfs.jimfs_1.1",
        "@maven//:com.sun.activation.javax.activation_1.2.0",
        "@maven//:commons-lang.commons-lang_2.4",
        "@maven//:javax.inject.javax.inject_1",
        "@maven//:junit.junit_4.12",
        "@maven//:org.codehaus.mojo.animal-sniffer-annotations_1.17",
        "@maven//:org.hamcrest.hamcrest-library_1.3",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect_1.4.32",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk7_1.4.32",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-android_1.4.1",
        "@maven//:xmlpull.xmlpull_1.1.3.1",
    ],
)
