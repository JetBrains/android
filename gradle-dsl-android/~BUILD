load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.gradle.dsl.android.tests",
    iml_files = ["intellij.android.gradle.dsl.android.tests.iml"],
    test_class = "com.android.tools.idea.gradle.dsl.GradleDslTestSuite",
    test_data = glob(["testData/**"]),
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl",
        "//tools/adt/idea/gradle-dsl-android:intellij.android.gradle.dsl.android",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl.testutils",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/.idea/libraries:junit4",
    ],
    test_srcs = ["testSrc"],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    deps = ["@intellij//:intellij-sdk"],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.gradle.dsl.android",
    srcs = ["src"],
    iml_files = ["intellij.android.gradle.dsl.android.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl",
    ],
)
