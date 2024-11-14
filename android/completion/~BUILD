load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.core.completion",
    iml_files = ["intellij.android.core.completion.iml"],
    test_class = "com.android.tools.idea.IdeaTestSuite",
    # keep sorted
    test_data = [
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android:test_deps",
        "//tools/adt/idea/android/testData",
        "//tools/base/build-system:android_gradle_plugin.zip",  # Please do not add old versions of AGP here. Use the old-agp-tests module instead.
        "//tools/base/build-system:android_gradle_plugin_runtime_dependencies",
        "//tools/base/build-system:gradle-distrib",
        "//tools/base/build-system/integration-test:kotlin_gradle_plugin_prebuilts",
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
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/android:intellij.android.core.tests[module, test]",
    ],
)
