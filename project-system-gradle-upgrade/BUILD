load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.projectSystem.gradle.upgrade.tests",
    iml_files = ["intellij.android.projectSystem.gradle.upgrade.tests.iml"],
    test_class = "com.android.tools.idea.gradle.project.upgrade.GradleProjectSystemUpgradeTestSuite",
    test_data = [
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android:test_deps",
        "//tools/adt/idea/android/testData",
        "//tools/base/build-system:gradle-distrib",
        "//tools/base/build-system:gradle-distrib-6.1.1",
        "//tools/base/build-system:gradle-distrib-6.5",
        "//tools/base/build-system:studio_repo",
        "//tools/base/build-system/previous-versions:kgp1.3.72",
    ],
    test_srcs = ["testSrc"],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//prebuilts/studio/intellij-sdk:studio-sdk[test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/android:intellij.android.core.tests[module, test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module, test]",
        "//tools/adt/idea/observable:intellij.android.observable[module, test]",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl[module, test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.projectSystem.gradle.upgrade",
    iml_files = ["intellij.android.projectSystem.gradle.upgrade.iml"],
    visibility = ["//visibility:public"],
)
