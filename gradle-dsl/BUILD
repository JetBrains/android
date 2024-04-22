load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.gradle.dsl",
    srcs = ["src"],
    iml_files = ["intellij.android.gradle.dsl.iml"],
    lint_baseline = "lint_baseline.xml",
    module_visibility = [
        # Language-specific implementation modules
        "//tools/adt/idea/gradle-dsl-kotlin:intellij.android.gradle.dsl.kotlin",
        "//tools/adt/idea/gradle-dsl-toml:intellij.android.gradle.dsl.toml",
        "//tools/adt/idea/gradle-dsl-declarative:intellij.android.gradle.dsl.declarative",
        # Gradle Project System tools
        "//tools/adt/idea/build-attribution:intellij.android.build-attribution",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle",
        "//tools/adt/idea/project-system-gradle-psd:intellij.android.projectSystem.gradle.psd",
        "//tools/adt/idea/project-system-gradle-upgrade:intellij.android.projectSystem.gradle.upgrade",
        # Templates and Wizards
        "//tools/adt/idea/android-npw:intellij.android.newProjectWizard",
        "//tools/adt/idea/android-templates:intellij.android.templates",
        # Kotlin Android Gradle Configurator
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.idea.common",
        # TODO(b/336267864): temporarily allowed until SomethingLanguage is
        #  separated from gradle-dsl-declarative
        "//tools/adt/idea/lint:intellij.lint",
        # TODO(xof): investigate
        "//tools/adt/idea/gmd-code-completion:intellij.android.gmd-code-completion",
        "//tools/studio/google/cloud/testing/test-recorder:test-recorder",
        "//tools/vendor/google/android-ndk:android-ndk",
        "//tools/vendor/google/firebase:firebase",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:org.intellij.groovy",
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
    test_shard_count = 3,
    test_srcs = ["testSrc"],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:com.intellij.gradle",
        "@intellij//:org.intellij.groovy",
        "@intellij//:org.jetbrains.kotlin",
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
        "@intellij//:org.toml.lang",
        "//tools/adt/idea/gradle-dsl-declarative:intellij.android.gradle.dsl.declarative[module, test]",
        "//tools/adt/idea/lint:intellij.lint[module, test]",
        "//tools/base/lint:studio.android.sdktools.lint-api[module, test]",
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
        "//tools/adt/idea/gradle-dsl-declarative:intellij.android.gradle.dsl.declarative",
        "@intellij//:org.intellij.groovy",
        "@intellij//:org.jetbrains.kotlin",
        "@intellij//:org.toml.lang",
    ],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:com.intellij.gradle",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/common:studio.android.sdktools.common[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//prebuilts/tools/common/m2:junit-4.12[test]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
    ],
)
