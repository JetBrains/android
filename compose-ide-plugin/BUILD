load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.compose-ide-plugin",
    srcs = ["src"],
    iml_files = ["intellij.android.compose-ide-plugin.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/adt/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/intellij.android.compose-common[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.compose-ide-plugin.tests",
    iml_files = ["intellij.android.compose-ide-plugin.tests.iml"],
    project = "unb",
    test_class = "com.android.tools.tests.ComposeTestSuite",
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module]",
        "//prebuilts/studio/intellij-sdk:studio-sdk[test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/compose-ide-plugin:intellij.android.compose-ide-plugin[module, test]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin[test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/android-plugin:intellij.android.plugin[module, test]",
    ],
)
