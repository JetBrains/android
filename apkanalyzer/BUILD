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
        "//tools/base/apkparser/analyzer:studio.android.sdktools.analyzer",
        "//tools/base/apkparser:studio.android.sdktools.binary-resources",
        "//tools/base/profgen/profgen:studio.android.sdktools.profgen",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common",
        "//tools/adt/idea/.idea/libraries:google-dexlib2",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/smali:intellij.android.smali",
        "//tools/adt/idea/android-lang:intellij.android.lang",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/base/threading-agent-callback:studio.android.sdktools.threading-agent-callback",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model",
        "//tools/base/testutils:studio.android.sdktools.testutils[test]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.apkanalyzer.tests",
    iml_files = ["intellij.android.apkanalyzer.tests.iml"],
    test_class = "com.android.tools.idea.ApkAnalyzerTestSuite",
    test_friends = ["//tools/adt/idea/apkanalyzer:intellij.android.apkanalyzer"],
    test_resources = ["testSrc/resources"],
    test_srcs = ["testSrc/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk[test]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[test]",
        "//tools/adt/idea/apkanalyzer:intellij.android.apkanalyzer[test]",
        "//tools/base/apkparser/analyzer:studio.android.sdktools.analyzer[test]",
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/apkparser:studio.android.sdktools.binary-resources[test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[test]",
        "//tools/adt/idea/.idea/libraries:kotlin-test[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin[test]",
    ],
)
