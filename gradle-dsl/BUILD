load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.gradle.dsl",
    srcs = ["src"],
    iml_files = ["intellij.android.gradle.dsl.iml"],
    lint_baseline = "lint_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Groovy",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.gradle.dsl.tests",
    iml_files = ["intellij.android.gradle.dsl.tests.iml"],
    test_class = "com.android.tools.idea.gradle.dsl.GradleDslTestSuite",
    test_data = glob(["testData/**"]),
    test_srcs = ["testSrc"],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Groovy",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl[module, test]",
        "//tools/adt/idea/gradle-dsl-kotlin:intellij.android.gradle.dsl.kotlin[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/common:studio.android.sdktools.common[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/project-system-gradle-models:intellij.android.projectSystem.gradle.models[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl.testutils[module, test]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/.idea/libraries:mockito",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.gradle.dsl.testutils",
    iml_files = ["intellij.android.gradle.dsl.testutils.iml"],
    test_srcs = ["testutils"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/adt/idea/gradle-dsl-kotlin:intellij.android.gradle.dsl.kotlin",
        "//tools/adt/idea/gradle-dsl-toml:intellij.android.gradle.dsl.toml",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Groovy",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-toml",
    ],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/common:studio.android.sdktools.common[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//prebuilts/tools/common/m2:junit-4.12[test]",
    ],
)
