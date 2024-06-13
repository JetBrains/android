load("//tools/base/bazel:bazel.bzl", "iml_module")

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
        "//tools/adt/idea/android:intellij.android.core[module]",
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
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.idea.common[module]",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.idea.k1[module]",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.idea.k2[module]",
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
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
        "//tools/base/sdk-common/gradle:studio.android.sdktools.sdk-common.gradle[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/intellij.android.compose-common[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl[module]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
        "//tools/adt/idea/render-resources:intellij.android.render-resources[module]",
        "//prebuilts/r8",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[module]",
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
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.idea.common[module]",
        "//tools/adt/idea/lint:intellij.lint[module]",
        "//tools/base/lint:studio.android.sdktools.lint-api[module]",
        "//tools/base/lint:studio.android.sdktools.lint-checks[module]",
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
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.idea.common[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
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
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.extensions.common[module]",
        "//tools/analytics-library/crash:analytics-crash[module]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[module]",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.tests[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
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
    generate_k2_tests = False,
    iml_files = ["intellij.android.kotlin.tests.iml"],
    split_test_targets = {
        "configure": {
            "test_filter": "org.jetbrains.kotlin.android.configure",
        },
        "other": {},
    },
    tags = [
        "no_test_mac",  # b/156004634
        "no_test_windows",
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
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.idea[module, test]",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.idea.common[module, test]",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.idea.k1[module, test]",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.idea.k2[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/base/common:studio.android.sdktools.common[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl.testutils[module, test]",
        "//tools/adt/idea/android-lint:intellij.android.lint[module, test]",
        "//tools/adt/idea/project-system-gradle-models:intellij.android.projectSystem.gradle.models[module, test]",
    ],
)
