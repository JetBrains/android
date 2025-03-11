load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.lint",
    srcs = ["src"],
    iml_files = ["intellij.lint.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:org.jetbrains.kotlin",
        "@intellij//:org.intellij.groovy",
        "@intellij//:org.jetbrains.plugins.gradle",
        "@intellij//:com.intellij.properties",
        "@intellij//:org.toml.lang",
        "//tools/base/lint:studio.android.sdktools.lint-checks",
        "//tools/adt/idea/gradle-declarative-lang:intellij.android.gradle.declarative.lang",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.lint.tests",
    generate_k1_tests = True,
    iml_files = ["tests/intellij.lint.tests.iml"],
    test_class = "com.android.tools.idea.lint.common.LintIdeTestSuite",
    test_data = [
        "//tools/adt/idea/android/annotations",
        "//tools/adt/idea/android/testData",
        "//tools/adt/idea/lint/tests/testData",
    ],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/lint:intellij.lint",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/base/lint:studio.android.sdktools.lint-checks",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
    ],
    test_resources = ["tests/testData"],
    test_srcs = ["tests/testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:com.intellij.gradle",
    ],
)
