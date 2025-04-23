load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_repository")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.mlkit",
    srcs = ["src"],
    iml_files = ["intellij.android.mlkit.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/base/mlkit-common:studio.android.sdktools.mlkit-common",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle",
        "//tools/adt/idea/wizard-model:intellij.android.wizard.model",
        "//tools/adt/idea/observable:intellij.android.observable",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/observable-ui:intellij.android.observable.ui",
        "//tools/adt/idea/wizard:intellij.android.wizard",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/base/wizard/template-plugin:studio.intellij.android.wizardTemplate.plugin",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/android-npw:intellij.android.newProjectWizard",
        "//tools/adt/idea/android-templates:intellij.android.templates",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.mlkit.tests",
    generate_k1_tests = True,
    iml_files = ["intellij.android.mlkit.tests.iml"],
    tags = [
        "noci:studio-win",  # b/135665870
    ],
    test_class = "com.android.tools.idea.mlkit.MlModelBindingTestSuite",
    test_data = [
        ":test_deps",
        "//prebuilts/studio/jdk/jdk11:jdk_runtime_files",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:platforms/latest",
        "//prebuilts/tools/common/mlkit/testData",
        "//tools/adt/idea/android/annotations",
        "//tools/adt/idea/android/testData",
        "//tools/adt/idea/mlkit/testData",
        "//tools/base/build-system:android_gradle_plugin.zip",
        "//tools/base/build-system:android_gradle_plugin_runtime_dependencies",
        "//tools/base/build-system:gradle-distrib",
    ],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/mlkit:intellij.android.mlkit",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/sdk-updates:intellij.android.sdkUpdates",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/base/wizard/template-plugin:studio.intellij.android.wizardTemplate.plugin",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/wizard-model:intellij.android.wizard.model",
        "//tools/base/mlkit-common:studio.android.sdktools.mlkit-common",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common",
        "//tools/adt/idea/project-system-gradle-models:intellij.android.projectSystem.gradle.models",
        "//tools/adt/idea/android-templates:intellij.android.templates",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle",
    ],
    test_resources = ["testData"],
    test_srcs = ["testSrc"],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:com.intellij.gradle",
    ],
)

maven_repository(
    name = "test_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:com.android.support.appcompat-v7_28.0.0",
        "@maven//:com.android.support.constraint.constraint-layout_1.0.2",
        "@maven//:com.android.support.test.espresso.espresso-core_3.0.2",
        "@maven//:com.google.auto.value.auto-value_1.6.2",
        "@maven//:com.google.guava.guava_19.0",
        "@maven//:com.google.jimfs.jimfs_1.1",
        "@maven//:com.sun.activation.javax.activation_1.2.0",
        "@maven//:commons-lang.commons-lang_2.4",
        "@maven//:org.codehaus.mojo.animal-sniffer-annotations_1.17",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect_1.4.32",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-common_1.4.32",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk7_1.4.32",
        "@maven//:xmlpull.xmlpull_1.1.3.1",
    ],
)
