load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_repository")
load("//tools/adt/idea/android/integration:build_defs.bzl", "INTEGRATION_TEST_GRADLE_VERSION")
load("//tools/adt/idea/studio:studio.bzl", "iml_studio_test")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.app-quality-insights.play-vitals.integration-tests",
    enable_tests = False,
    iml_files = ["intellij.android.app-quality-insights.play-vitals.integration-tests.iml"],
    test_resources = ["testData"],
    test_srcs = ["src"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk[test]",
        "//tools/adt/idea/as-driver:as-driver.utils[module, test]",
        "//tools/adt/idea/app-quality-insights/api:intellij.android.app-quality-insights.api[module, test]",
        "//tools/adt/idea/app-quality-insights/play-vitals/model:intellij.android.app-quality-insights.play-vitals.model[module, test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/.idea/libraries:studio-grpc[test]",
    ],
)

filegroup(
    name = "vitals_test_project",
    srcs = glob(["testData/projects/appinsights/**"]),
)

maven_repository(
    name = "openproject_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:com.android.tools.build.gradle_7.1.0",
        "@maven//:org.jetbrains.kotlin.kotlin-compiler_1.9.20-Beta",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin-api_1.9.20-Beta",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_1.9.20-Beta",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.9.20-Beta",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core_1.4.1",
        "@maven//:org.jetbrains.markdown_0.2.1",
    ],
)

iml_studio_test(
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
