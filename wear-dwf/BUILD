load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.wear-dwf",
    # do not sort: must match IML order
    srcs = [
        "src",
        "gen",
    ],
    iml_files = ["intellij.android.wear-dwf.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/base/wear-wff-schema:studio.intellij.android.wear-wff-schema",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/base/apkparser/analyzer:studio.android.sdktools.analyzer",
        "//tools/base/resource-repository:studio.android.sdktools.resource-repository",
        "//tools/adt/idea/apkanalyzer:intellij.android.apkanalyzer",
        "//tools/adt/idea/.idea/libraries:aapt-proto",
        "//tools/base/build-system:studio.android.sdktools.manifest-merger",
        "//tools/base/apkparser:studio.android.sdktools.binary-resources",
        "//tools/adt/idea/jps-model:intellij.android.jps.model",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/analytics-library/tracker:analytics-tracker",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.wear-dwf.tests",
    iml_files = ["intellij.android.wear-dwf.tests.iml"],
    test_class = "com.android.tools.idea.wear.dwf.AndroidWearDWFTestSuite",
    test_data = [
        "//prebuilts/studio/sdk:platform-tools",
        "//tools/adt/idea/wear-dwf/testData",
    ],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:kotlin-test",
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
        "@intellij//:test-framework",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/project-system-gradle-models:intellij.android.projectSystem.gradle.models",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/android-lang:intellij.android.lang.tests",
    ],
    test_friends = ["//tools/adt/idea/wear-dwf:intellij.android.wear-dwf"],
    test_resources = ["testData"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "//tools/adt/idea/wear-dwf:intellij.android.wear-dwf",
        "//tools/base/wear-wff-schema:studio.intellij.android.wear-wff-schema",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/base/common:studio.android.sdktools.common",
    ],
)
