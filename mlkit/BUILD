load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.mlkit",
    srcs = ["src"],
    iml_files = ["intellij.android.mlkit.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/base/mlkit-common:studio.android.sdktools.mlkit-common[module]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
        "//tools/adt/idea/wizard-model:intellij.android.wizard.model[module]",
        "//tools/adt/idea/observable:intellij.android.observable[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/adt/idea/observable-ui:intellij.android.observable.ui[module]",
        "//tools/adt/idea/wizard:intellij.android.wizard[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/base/wizard/template-plugin:studio.intellij.android.wizardTemplate.plugin[module]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.mlkit.tests",
    iml_files = ["intellij.android.mlkit.tests.iml"],
    tags = [
        "no_test_windows",  # b/135665870
    ],
    test_class = "com.android.tools.idea.mlkit.MlModelBindingTestSuite",
    test_data = [
        "//prebuilts/studio/jdk",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:platforms/latest",
        "//prebuilts/tools/common/mlkit/testData",
        "//tools/adt/idea/android/annotations",
        "//tools/adt/idea/android/testData",
        "//tools/adt/idea/android:test_deps",
        "//tools/adt/idea/mlkit/testData",
        "//tools/base/build-system:gradle-distrib",
        "//tools/base/build-system:studio_repo",
    ],
    test_resources = ["testData"],
    test_srcs = ["testSrc"],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle",
        "//tools/adt/idea/mlkit:intellij.android.mlkit[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/sdk-updates:intellij.android.sdkUpdates[module, test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module, test]",
        "//tools/analytics-library/tracker:analytics-tracker[module, test]",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing[module, test]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto[test]",
        "//tools/base/wizard/template-plugin:studio.intellij.android.wizardTemplate.plugin[module, test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module, test]",
        "//tools/adt/idea/wizard-model:intellij.android.wizard.model[module, test]",
        "//tools/base/mlkit-common:studio.android.sdktools.mlkit-common[module, test]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module, test]",
        "//tools/adt/idea/project-system-gradle-models:intellij.android.projectSystem.gradle.models[module, test]",
    ],
)
