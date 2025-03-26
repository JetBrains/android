load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.gmd-code-completion",
    srcs = ["src"],
    iml_files = ["intellij.android.gmd-code-completion.iml"],
    resources = ["resources"],
    test_class = "com.android.gmdcodecompletion.GmdCodeCompletionTestSuite",
    test_data = [
        "//prebuilts/studio/sdk:platforms/latest-preview",
        "//tools/adt/idea/android/testData",
    ],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
    ],
    test_srcs = ["testSrc"],
    test_tags = [
        "block-network",
        "noci:studio-win",
    ],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/studio/google/cloud/testing/firebase-testing",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/base/sdklib:studio.android.sdktools.sdklib",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl",
        "//tools/studio/google/cloud/tools/google-login-plugin:google-login-as",
        "@intellij//:org.intellij.groovy",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle",
        "//tools/adt/idea/.idea/libraries:google-api-client",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
    ],
)
