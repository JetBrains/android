load("//tools/adt/idea/android/integration:build_defs.bzl", "INTEGRATION_TEST_GRADLE_VERSION", "KOTLIN_ARTIFACTS_FOR_TESTS", "KOTLIN_VERSION_FOR_TESTS")
load("//tools/base/bazel:bazel.bzl", "iml_module", "iml_test")
load("//tools/base/bazel:maven.bzl", "maven_repository")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.app-quality-insights.play-vitals.integration-tests",
    enable_tests = False,
    iml_files = ["intellij.android.app-quality-insights.play-vitals.integration-tests.iml"],
    # do not sort: must match IML order
    test_deps = [
        "@intellij//:intellij-sdk",
        "//tools/adt/idea/as-driver:as-driver.utils",
        "//tools/adt/idea/app-quality-insights/client:intellij.android.app-quality-insights.client",
        "//tools/adt/idea/app-quality-insights/play-vitals/model:intellij.android.app-quality-insights.play-vitals.model",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/adt/idea/.idea/libraries:truth",
    ],
    test_resources = ["testData"],
    test_srcs = ["src"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = ["@intellij//:intellij.libraries.grpc"],
)

filegroup(
    name = "vitals_test_project",
    srcs = glob(["testData/projects/appinsights/**"]),
)

maven_repository(
    name = "openproject_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:com.android.application.com.android.application.gradle.plugin_9.0.0",
        "@maven//:com.android.tools.build.aapt2_9.0.0-14304508",
        "@maven//:com.android.tools.build.gradle_9.0.0",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core_1.4.1",
        "@maven//:org.jetbrains.markdown_0.2.1",
    ] + KOTLIN_ARTIFACTS_FOR_TESTS,
)

iml_test(
    name = "VitalsIntegrationTest",
    timeout = "long",
    data = [
        ":openproject_deps",
        ":vitals_test_project",
        INTEGRATION_TEST_GRADLE_VERSION,
    ],
    module = ":intellij.android.app-quality-insights.play-vitals.integration-tests",
    tags_linux = ["block-network"],
    tags_windows = ["block-network"],
    test_class = "com.android.tools.idea.vitals.VitalsIntegrationTest",
)
