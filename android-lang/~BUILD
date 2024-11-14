load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.lang",
    # do not sort: must match IML order
    srcs = [
        "src",
        "gen",
        "resources",
    ],
    iml_files = ["intellij.android.lang.iml"],
    lint_baseline = "lint_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:org.jetbrains.kotlin",
        "@intellij//:org.intellij.intelliLang",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.lang.tests",
    iml_files = ["intellij.android.lang.tests.iml"],
    split_test_targets = {
        "agsl": {
            "test_filter": "com.android.tools.idea.lang.agsl",
        },
        "aidl": {
            "test_filter": "com.android.tools.idea.lang.aidl",
        },
        "androidSql": {
            "test_filter": "com.android.tools.idea.lang.androidSql",
        },
        "multiDexKeep": {
            "test_filter": "com.android.tools.idea.lang.multiDexKeep",
        },
        "proguardR8": {
            "test_filter": "com.android.tools.idea.lang.proguardR8",
        },
        "rs": {
            "test_filter": "com.android.tools.idea.lang.rs",
        },
        "other": {},
    },
    tags = [
        "noci:studio-win",  # b/135665870
    ],
    test_class = "com.android.tools.idea.lang.AndroidLangTestSuite",
    test_data = glob(["testData/**"]) + [
        "//prebuilts/studio/jdk/jdk11",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/annotations",
        "//tools/adt/idea/android/testData",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:com.intellij.gradle",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/android-lang:intellij.android.lang[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
        "//tools/adt/idea/project-system-gradle-models:intellij.android.projectSystem.gradle.models[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module]",
    ],
)
