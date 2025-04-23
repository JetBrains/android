load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:jvm_import.bzl", "jvm_import")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.debuggers",
    srcs = ["src"],
    iml_files = ["intellij.android.debuggers.iml"],
    lint_baseline = "lint_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/deploy:intellij.android.deploy",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/base/flags:studio.android.sdktools.flags",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/debuggers:libjava_sites",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/analytics-library/tracker:analytics-tracker",
        "@intellij//:com.intellij.platform.images",
        "//tools/base/lint:studio.android.sdktools.lint-api",
        "//tools/adt/idea/execution/common:intellij.android.execution.common",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
        "//tools/adt/idea/analytics",
        "//tools/base/kexter:studio.android.sdktools.kexter",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/base/zipflinger:studio.android.sdktools.zipflinger",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.debuggers.tests",
    iml_files = ["intellij.android.debuggers.tests.iml"],
    test_class = "com.android.tools.idea.debuggers.DebuggersTestSuite",
    # keep sorted
    test_data = [
        "//prebuilts/studio/jdk/jdk11:jdk_runtime_files",
        "//prebuilts/studio/sdk:platforms/android-30",
        "//prebuilts/studio/sdk:platforms/latest",
        "//prebuilts/studio/sdk:sources",
        "//tools/adt/idea/android/testData",
    ],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/.idea/libraries:kotlin-test",
        "//tools/adt/idea/debuggers:intellij.android.debuggers",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/deploy:intellij.android.deploy",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/base/lint:studio.android.sdktools.lint-api",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/execution/common:intellij.android.execution.common",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
    ],
    test_friends = ["//tools/adt/idea/debuggers:intellij.android.debuggers"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
    ],
)

# managed by go/iml_to_build
jvm_import(
    name = "libjava_sites",
    jars = ["//tools/base/deploy/sites:libjava_sites.jar"],
    visibility = ["//visibility:public"],
)

kotlin_library(
    name = "android-field-visibility-provider",
    srcs = ["src/com/android/tools/idea/debug/AndroidFieldVisibilityProvider.kt"],
    coverage_baseline_enabled = False,
    lint_enabled = False,
    visibility = ["//visibility:public"],
    deps = [
        "//tools/adt/idea/debuggers:intellij.android.debuggers",
        "@intellij//:com.intellij.java",
    ],
)
