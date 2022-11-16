load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.navigator",
    srcs = ["src"],
    iml_files = ["intellij.android.navigator.iml"],
    lint_baseline = "lint_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/apkanalyzer:intellij.android.apkanalyzer[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/android-lang:intellij.android.lang[module]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
        "//tools/adt/idea/project-system-gradle-models:intellij.android.projectSystem.gradle.models[module]",
        "//tools/adt/idea/smali:intellij.android.smali[module]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/adt/idea/.idea/libraries:dexlib2",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.navigator.testutils",
    iml_files = ["intellij.android.navigator.testutils.iml"],
    test_srcs = ["testUtils"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/testutils:studio.android.sdktools.testutils[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/android-navigator:intellij.android.navigator[module]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module]",
        "//tools/adt/idea/.idea/libraries:truth",
    ],
)
