load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.core.lang",
    iml_files = ["intellij.android.core.lang.iml"],
    test_class = "com.android.tools.idea.IdeaTestSuite",
    # keep sorted
    test_data = [
        "//tools/adt/idea/android/testData",
    ],
    test_friends = ["//tools/adt/idea/android:intellij.android.core"],
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
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/android:intellij.android.core.tests[module, test]",
    ],
)
