load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:diff.bzl", "apply_diff")
load("//tools/base/bazel:gradle.bzl", "gradle_build")

# managed by go/iml_to_build [unb]
iml_module(
    name = "intellij.android.projectSystem.gradle",
    srcs = ["src"],
    iml_files = ["intellij.android.projectSystem.gradle.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    project = "unb",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-junit",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/.idea/libraries:unb.kotlin-stdlib-jdk8",
        "//tools/base/apkparser/analyzer:studio.android.sdktools.analyzer[module]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
        "//tools/adt/idea/.idea/libraries:unb.protobuf",
        "//tools/adt/idea/.idea/libraries:unb.studio-analytics-proto",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.extensions[module]",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl[module]",
        "//tools/adt/idea/deploy:intellij.android.deploy[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/adt/idea/apkanalyzer:intellij.android.apkanalyzer[module]",
        "//tools/adt/idea/.idea/libraries:unb.studio-proto",
    ],
)

# managed by go/iml_to_build [unb]
iml_module(
    name = "intellij.android.projectSystem.gradle.tests",
    iml_files = ["intellij.android.projectSystem.gradle.tests.iml"],
    project = "unb",
    tags = ["no_test_windows"],
    test_class = "com.android.tools.idea.projectsystem.GradleProjectSystemTestSuite",
    test_data = [
        "//tools/base/build-system/previous-versions:2.2.0.zip",
        "//prebuilts/studio/jdk",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:platforms/latest",
        "//prebuilts/tools/common/kotlin-plugin:Kotlin/kotlinc/build.txt",
        "//tools/adt/idea/android:test_deps.zip",
        "//tools/adt/idea/android/annotations",
        "//tools/adt/idea/android/testData",
        "//tools/adt/idea/designer/testData",
        "//tools/adt/idea/project-system-gradle/testData",
        "//tools/base/build-system:gradle-distrib",
        "//tools/base/build-system:studio_repo.zip",
        "//tools/base/third_party/kotlin:kotlin-m2repository.zip",
    ],
    test_shard_count = 2,
    test_srcs = ["testSrc"],
    test_tags = [
        "cpu:3",
        "block-network",
        "no_test_mac",
    ],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module, test]",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/.idea/libraries:unb.mockito[test]",
        "//tools/adt/idea/.idea/libraries:unb.truth[test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl[module, test]",
        "//tools/adt/idea/.idea/libraries:unb.studio-analytics-proto[test]",
        "//tools/adt/idea/.idea/libraries:unb.studio-proto[test]",
        "//tools/adt/idea/.idea/libraries:unb.protobuf[test]",
        "//tools/adt/idea/deploy:intellij.android.deploy[module, test]",
    ],
)

# Remove remote publishing from upstream jetbrains script
apply_diff(
    name = "fixup_build.gradle",
    diff_file = "build_gradle.diff",
    input_file = "//tools/idea/build/dependencies/gradle-api-deps:build.gradle",
    output_file = "build.gradle",
    tags = ["manual"],
)

# generate the maven repository
gradle_build(
    name = "idea_gradle_api",
    build_file = ":build.gradle",
    output_files = {
        "gradle-api-impldep.jar": "m2/org/jetbrains/intellij/deps/gradle-api-impldep/*/gradle-api-impldep-*.jar",
        "gradle-api.jar": "m2/org/jetbrains/intellij/deps/gradle-api/*/gradle-api-*.jar",
        "gradle-launcher.jar": "m2/org/jetbrains/intellij/deps/gradle-launcher/*/gradle-launcher-*.jar",
        "gradle-wrapper.jar": "m2/org/jetbrains/intellij/deps/gradle-wrapper/*/gradle-wrapper-*.jar",
    },
    tags = ["manual"],
    tasks = [":publishGradleApiToMavenLocal"],
)
