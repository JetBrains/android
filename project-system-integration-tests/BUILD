load("//tools/adt/idea/android/integration:build_defs.bzl", "INTEGRATION_TEST_GRADLE_VERSION")
load("//tools/adt/idea/studio:studio.bzl", "iml_studio_test")
load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_repository")
load("//tools/base/build-system/integration-test:common-dependencies.bzl", "KGP_VERSION_FOR_TESTS")

common_data = [
    ":sdkindexapp",
    ":sdkindexproject_deps",
    ":sdkindexsnapshot",
    "//prebuilts/studio/sdk:build-tools/latest",
    INTEGRATION_TEST_GRADLE_VERSION,
]

old_project_app_data = [
    ":oldprojectapp",
    ":oldprojectapp_deps",
    "//prebuilts/studio/jdk/jdk11",
    "//prebuilts/studio/sdk:build-tools/latest",
    "//tools/base/build-system:gradle-distrib-6.7.1",
]

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.projectSystem.integration.tests",
    enable_tests = False,
    iml_files = ["intellij.android.projectSystem.integration.tests.iml"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk[test]",
        "@intellij//:com.intellij.java",
        "//prebuilts/tools/common/m2:junit-4.12[test]",
        "//tools/adt/idea/as-driver:as-driver.utils[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/base/common:studio.android.sdktools.common[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/lint:studio.android.sdktools.lint-checks[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
    ],
)

iml_studio_test(
    name = "SdkIndexLintTest",
    timeout = "long",
    data = common_data,
    module = ":intellij.android.projectSystem.integration.tests",
    tags_linux = ["block-network"],
    # Commented out due to b/242358149
    # tags_mac = ["block-network"],
    tags_windows = ["block-network"],
    test_class = "com.android.tools.idea.projectsystem.gradle.SdkIndexLintTest",
)

iml_studio_test(
    name = "SdkIndexLintWithoutNotesTest",
    timeout = "long",
    data = common_data,
    module = ":intellij.android.projectSystem.integration.tests",
    tags_linux = ["block-network"],
    # Commented out due to b/242358149
    # tags_mac = ["block-network"],
    tags_windows = ["block-network"],
    test_class = "com.android.tools.idea.projectsystem.gradle.SdkIndexLintWithoutNotesTest",
)

iml_studio_test(
    name = "SdkIndexPsdTest",
    timeout = "long",
    data = common_data,
    module = ":intellij.android.projectSystem.integration.tests",
    tags_linux = ["block-network"],
    # Commented out due to b/242358149
    # tags_mac = ["block-network"],
    tags_windows = ["block-network"],
    test_class = "com.android.tools.idea.projectsystem.gradle.SdkIndexPsdTest",
)

iml_studio_test(
    name = "SdkIndexPsdWithoutNotesTest",
    timeout = "long",
    data = common_data,
    module = ":intellij.android.projectSystem.integration.tests",
    tags_linux = ["block-network"],
    # Commented out due to b/242358149
    # tags_mac = ["block-network"],
    tags_windows = ["block-network"],
    test_class = "com.android.tools.idea.projectsystem.gradle.SdkIndexPsdWithoutNotesTest",
)

iml_studio_test(
    name = "OldProjectAppTest",
    timeout = "long",
    data = old_project_app_data,
    module = ":intellij.android.projectSystem.integration.tests",
    tags_linux = ["block-network"],
    # Commented out due to b/242358149
    # tags_mac = ["block-network"],
    tags_windows = ["block-network"],
    test_class = "com.android.tools.idea.projectsystem.gradle.OldProjectAppTest",
)

iml_studio_test(
    name = "GradleTokensTest",
    timeout = "long",
    module = ":intellij.android.projectSystem.integration.tests",
    tags_linux = ["block-network"],
    # Commented out due to b/242358149
    # tags_mac = ["block-network"],
    tags_windows = ["block-network"],
    test_class = "com.android.tools.idea.projectsystem.gradle.GradleTokensTest",
)

maven_repository(
    name = "sdkindexproject_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:com.android.tools.build.gradle_8.1.0",
        "@maven//:org.jetbrains.kotlin.kotlin-compiler_" + KGP_VERSION_FOR_TESTS,
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin-api_" + KGP_VERSION_FOR_TESTS,
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_" + KGP_VERSION_FOR_TESTS,
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_" + KGP_VERSION_FOR_TESTS,
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core_1.4.1",
        "@maven//:org.jetbrains.markdown_0.2.1",
    ],
)

maven_repository(
    name = "oldprojectapp_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:com.android.tools.build.gradle_4.2.0",
        "@maven//:org.jetbrains.kotlin.kotlin-compiler_1.7.20",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin-api_1.7.20",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_1.7.20",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.7.20",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core_1.4.1",
        "@maven//:org.jetbrains.markdown_0.2.1",
    ],
)

filegroup(
    name = "sdkindexapp",
    srcs = glob(["testData/sdkindexapp/**"]),
)

filegroup(
    name = "sdkindexsnapshot",
    srcs = glob(["testData/snapshot/**"]),
)

filegroup(
    name = "oldprojectapp",
    srcs = glob(["testData/oldprojectapp/**"]),
)
