load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.projectSystem.gradle.sync",
    srcs = ["src"],
    iml_files = ["intellij.android.projectSystem.gradle.sync.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-junit",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[module]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.projectSystem.gradle.sync.tests",
    iml_files = ["intellij.android.projectSystem.gradle.sync.tests.iml"],
    test_class = "com.android.tools.idea.projectsystem.gradle.sync.GradleProjectSystemSyncTestSuite",
    test_data = [
        "//tools/base/build-system/previous-versions:2.2.0.zip",
        "//prebuilts/studio/jdk",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:platforms/latest",
        "//prebuilts/tools/common/kotlin-plugin:Kotlin/kotlinc/build.txt",
        "//tools/adt/idea/android:test_deps.zip",
        "//tools/adt/idea/android/annotations",
        "//tools/adt/idea/android/testData",
        "//tools/base/build-system:gradle-distrib",
        "//tools/base/build-system:studio_repo.zip",
        "//tools/base/third_party/kotlin:kotlin-m2repository.zip",
    ],
    test_srcs = ["testSrc"],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-junit",
        "//tools/adt/idea/project-system-gradle-sync:intellij.android.projectSystem.gradle.sync[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[module, test]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle[test]",
    ],
)
