load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.core.actions",
    iml_files = ["intellij.android.core.actions.iml"],
    test_class = "com.android.tools.idea.IdeaTestSuite",
    # keep sorted
    test_data = [
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/testData",
    ],
    test_jvm_flags = [
        "-Djdk.attach.allowAttachSelf=true",
    ],
    test_srcs = ["testSrc"],
    test_tags = [
        "block-network",
    ],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/project-system-gradle-psd:intellij.android.projectSystem.gradle.psd",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "//tools/adt/idea/android:intellij.android.core[test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[test]",
        "//tools/base/flags:studio.android.sdktools.flags[test]",
        "//tools/adt/idea/android-common:intellij.android.common[test]",
        "//tools/base/lint:studio.android.sdktools.lint-checks[test]",
        "//tools/adt/idea/android:intellij.android.core.tests[test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[test]",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin[test]",
    ],
)
