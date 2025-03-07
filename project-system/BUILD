load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.projectSystem",
    srcs = ["src"],
    iml_files = ["intellij.android.projectSystem.iml"],
    lint_baseline = "lint_baseline.xml",
    test_tags = ["manual"],  # testSrc here is for related test infrastructure, not tests
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common",
        "//tools/base/build-system:studio.android.sdktools.manifest-merger",
        "//tools/base/lint:studio.android.sdktools.lint-api",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/jps-model:intellij.android.jps.model",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.projectSystem.tests",
    iml_files = ["intellij.android.projectSystem.tests.iml"],
    test_class = "com.android.tools.idea.projectsystem.ProjectSystemTestSuite",
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[test]",
        "//tools/adt/idea/android-common:intellij.android.common[test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[test]",
        "//tools/base/common:studio.android.sdktools.common[test]",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin[test]",
    ],
)
