load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.gradle.dsl.android.tests",
    iml_files = ["intellij.android.gradle.dsl.android.tests.iml"],
    test_class = "com.android.tools.idea.gradle.dsl.GradleDslTestSuite",
    test_data = glob(["testData/**"]),
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl",
        "//tools/adt/idea/gradle-dsl-android:intellij.android.gradle.dsl.android",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl.testutils",
        "//tools/adt/idea/project-system-gradle-models:intellij.android.projectSystem.gradle.models",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/gradle-declarative-lang-flags:intellij.android.gradle.declarative.lang.flags",
        "//tools/adt/idea/gradle-dsl-flags:intellij.android.gradle.dsl.flags",
    ],
    test_srcs = ["testSrc"],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.gradle.dsl.android",
    srcs = ["src"],
    iml_files = ["intellij.android.gradle.dsl.android.iml"],
    module_visibility = [
        # Gradle Project System tools
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle",
        "//tools/adt/idea/project-system-gradle-psd:intellij.android.projectSystem.gradle.psd",
        "//tools/adt/idea/project-system-gradle-upgrade:intellij.android.projectSystem.gradle.upgrade",
        # Templates and Wizards
        "//tools/adt/idea/android-npw:intellij.android.newProjectWizard",
        "//tools/adt/idea/android-templates:intellij.android.templates",
        # Kotlin Android Gradle Configurator
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.idea.common",
        # Project System Token implementation modules
        "//tools/studio/google/cloud/testing/test-recorder/gradle:test-recorder.gradle",
        # Android Ndk
        "//tools/vendor/google/android-ndk:android-ndk",
        # AI plugin
        "//tools/vendor/google/ml/aiplugin/android/gradle:aiplugin.android.gradle",
    ],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl",
        "//tools/adt/idea/gradle-dsl-plugin:intellij.android.gradle.dsl.plugin",
    ],
)
