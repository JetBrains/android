load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.core.editing.documentation",
    srcs = ["src"],
    iml_files = ["intellij.android.core.editing.documentation.iml"],
    # TODO: b/405445548 - Remove once flakiness on Windows has been addressed.
    tags = ["noci:studio-win"],
    test_class = "com.android.tools.idea.IdeaTestSuite",
    # keep sorted
    test_data = [
        "//prebuilts/studio/layoutlib:runtime",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:layoutlib_extensions",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/editing/documentation/testData",
    ],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/base/lint:studio.android.sdktools.lint-api",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/android:intellij.android.core.tests",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/adt/idea/.idea/libraries:kotlin-test",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test",
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/base/resource-repository:studio.android.sdktools.resource-repository",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
        "//tools/adt/idea/rendering:intellij.android.rendering",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/vendor/google/android-keys",
    ],
)
