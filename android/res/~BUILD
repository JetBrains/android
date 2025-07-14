load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.core.res",
    iml_files = ["intellij.android.core.res.iml"],
    test_class = "com.android.tools.idea.IdeaTestSuite",
    test_data = [
        "//prebuilts/studio/layoutlib:runtime",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:layoutlib_extensions",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/testData",
        "//tools/base/resource-repository/test/resources",
    ],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/base/lint:studio.android.sdktools.lint-api",
        "//tools/base/resource-repository:studio.android.sdktools.resource-repository",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/android:intellij.android.core.tests",
        "//tools/adt/idea/jps-model:intellij.android.jps.model",
        "//tools/adt/idea/layoutlib:intellij.android.layoutlib",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/project-system-gradle-models:intellij.android.projectSystem.gradle.models",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/adt/idea/.idea/libraries:kotlin-test",
        "//tools/adt/idea/.idea/libraries:mockito",
        "@intellij//:intellij-sdk",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/base/flags:studio.android.sdktools.flags",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
    ],
    test_srcs = ["testSrc"],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = ["@intellij//:com.intellij.java"],
)
