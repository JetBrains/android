load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.kotlin.output.parser",
    srcs = ["idea-android/idea-android-output-parser/src"],
    iml_files = ["idea-android/idea-android-output-parser/intellij.android.kotlin.output.parser.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/android:intellij.android.core",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.kotlin.idea",
    srcs = ["idea-android/src"],
    iml_files = ["idea-android/intellij.android.kotlin.idea.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:org.jetbrains.kotlin",
        "@intellij//:com.intellij.gradle",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.idea.common",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.idea.k1",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.idea.k2",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.kotlin.idea.common",
    srcs = ["idea-android/common/src"],
    iml_files = ["idea-android/common/intellij.android.kotlin.idea.common.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common",
        "//tools/base/sdk-common/gradle:studio.android.sdktools.sdk-common.gradle",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/intellij.android.compose-common",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
        "//prebuilts/r8",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle",
        "//tools/adt/idea/gmaven:intellij.android.gmaven",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.kotlin.idea.k1",
    srcs = ["idea-android/k1/src"],
    iml_files = ["idea-android/k1/intellij.android.kotlin.idea.k1.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.idea.common",
        "//tools/adt/idea/lint:intellij.lint",
        "//tools/base/lint:studio.android.sdktools.lint-api",
        "//tools/base/lint:studio.android.sdktools.lint-checks",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.kotlin.idea.k2",
    srcs = ["idea-android/k2/src"],
    iml_files = ["idea-android/k2/intellij.android.kotlin.idea.k2.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.idea.common",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api",
        "//tools/adt/idea/android-common:intellij.android.common",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.kotlin.extensions",
    srcs = ["android-extensions-idea/src"],
    iml_files = ["android-extensions-idea/intellij.android.kotlin.extensions.iml"],
    test_class = "org.jetbrains.kotlin.android.KotlinAndroidTestSuite",
    test_data = glob([
        "android-extensions-idea/testData/**",
    ]) + [
        "idea-android/testData/android/AndroidManifest.xml",
        "//prebuilts/studio/sdk:platforms/latest",
    ],
    test_srcs = ["android-extensions-idea/tests"],
    test_tags = [
        # Kotlin synthetics are deprecated and will not be supported with Kotlin K2.
        # See https://android-developers.googleblog.com/2022/02/discontinuing-kotlin-synthetics-for-views.html
        "no_k2",
    ],
    test_timeout = "long",  # These tests can be slow, especially on Mac (b/299445080).
    visibility = ["//visibility:public"],
    exports = ["//tools/adt/idea/android-kotlin:intellij.android.kotlin.extensions.common"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.extensions.common",
        "//tools/analytics-library/crash:analytics-crash",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.tests[test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[test]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.kotlin.extensions.common",
    srcs = ["android-extensions-idea-common/src"],
    iml_files = ["android-extensions-idea-common/intellij.android.kotlin.extensions.common.iml"],
    jvm_target = "8",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:com.intellij.gradle",
        "@intellij//:org.jetbrains.kotlin",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.kotlin.tests",
    generate_k1_tests = True,
    iml_files = ["intellij.android.kotlin.tests.iml"],
    split_test_targets = {
        "configure": {
            "test_filter": "org.jetbrains.kotlin.android.configure",
        },
        "other": {},
    },
    tags = [
        "noci:studio-win",
    ],
    test_class = "org.jetbrains.kotlin.android.KotlinAndroidTestSuite",
    test_data = glob([
        "idea-android/testData/**",
    ]) + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/annotations",
    ],
    test_srcs = ["idea-android/tests"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:com.intellij.gradle",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "//tools/adt/idea/android:intellij.android.core[test]",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.idea[test]",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.idea.common[test]",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.idea.k1[test]",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.idea.k2[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[test]",
        "//tools/base/common:studio.android.sdktools.common[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[test]",
        "//tools/base/flags:studio.android.sdktools.flags[test]",
        "//tools/adt/idea/android-common:intellij.android.common[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl.testutils[test]",
        "//tools/adt/idea/android-lint:intellij.android.lint[test]",
        "//tools/adt/idea/project-system-gradle-models:intellij.android.projectSystem.gradle.models[test]",
        "//tools/adt/idea/.idea/libraries:opentest4j[test]",
    ],
)

kotlin_library(
    name = "android-dexer",
    srcs = ["idea-android/common/src/org/jetbrains/kotlin/android/debugger/AndroidDexerImpl.kt"],
    coverage_baseline_enabled = False,
    lint_enabled = False,
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/r8",
        "@intellij//:intellij-sdk",
        "@intellij//:org.jetbrains.kotlin",
    ],
)
