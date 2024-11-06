load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.apkanalyzer",
    srcs = ["src"],
    iml_files = ["intellij.android.apkanalyzer.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/.idea/libraries:aapt-proto",
        "//tools/base/apkparser/analyzer:studio.android.sdktools.analyzer[module]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/base/apkparser:studio.android.sdktools.binary-resources[module]",
        "//tools/base/profgen/profgen:studio.android.sdktools.profgen[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
        "//tools/adt/idea/.idea/libraries:google-dexlib2",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
        "//tools/adt/idea/smali:intellij.android.smali[module]",
        "//tools/adt/idea/android-lang:intellij.android.lang[module]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/base/threading-agent-callback:studio.android.sdktools.threading-agent-callback[module]",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.apkanalyzer.tests",
    iml_files = ["intellij.android.apkanalyzer.tests.iml"],
    test_class = "com.android.tools.idea.ApkAnalyzerTestSuite",
    test_resources = ["testSrc/resources"],
    test_srcs = ["testSrc/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk[test]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module, test]",
        "//tools/adt/idea/apkanalyzer:intellij.android.apkanalyzer[module, test]",
        "//tools/base/apkparser/analyzer:studio.android.sdktools.analyzer[module, test]",
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
    ],
)
