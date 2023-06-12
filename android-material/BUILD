load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.android-material",
    srcs = ["src"],
    iml_files = ["intellij.android.android-material.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.android-material.tests",
    iml_files = ["intellij.android.android-material.tests.iml"],
    test_class = "com.android.tools.idea.MaterialTestSuite",
    test_srcs = [
        "testResources",
        "testSrc",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:kotlin-test[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/common:studio.android.sdktools.common[module, test]",
        "//tools/base/sdklib:studio.android.sdktools.sdklib[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/android-material:intellij.android.android-material[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
    ],
)
