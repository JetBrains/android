load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.android-material",
    srcs = ["src"],
    iml_files = ["intellij.android.android-material.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android:intellij.android.core",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.android-material.tests",
    iml_files = ["intellij.android.android-material.tests.iml"],
    test_class = "com.android.tools.idea.MaterialTestSuite",
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/.idea/libraries:kotlin-test",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/base/sdklib:studio.android.sdktools.sdklib",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/adt/idea/android-material:intellij.android.android-material",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
        "//tools/adt/idea/android-common:intellij.android.common",
    ],
    test_friends = ["//tools/adt/idea/android-material:intellij.android.android-material"],
    test_resources = ["testResources"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
    ],
)
