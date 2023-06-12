load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.gmd-code-completion",
    srcs = ["src"],
    iml_files = ["intellij.android.gmd-code-completion.iml"],
    test_class = "com.android.gmdcodecompletion.GmdCodeCompletionTestSuite",
    test_data = [
        "//prebuilts/studio/sdk:platforms/latest-preview",
        "//tools/adt/idea/android/testData",
    ],
    test_srcs = ["testSrc"],
    test_tags = [
        "block-network",
        "no_test_windows",
    ],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/studio/google/cloud/testing/firebase-testing[module]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/base/sdklib:studio.android.sdktools.sdklib[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl[module]",
        "//tools/studio/google/cloud/testing/firebase-testing/lib:google-api-services-testing-v1-revsnapshot-1.20.0",
        "//tools/studio/google/cloud/tools/google-login-plugin:google-login-as[module]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Groovy",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/render-resources:intellij.android.render-resources[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[module]",
    ],
)
