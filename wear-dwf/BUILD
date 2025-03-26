load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.wear-dwf",
    srcs = ["src"],
    iml_files = ["intellij.android.wear-dwf.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/base/wear-wff-schema:studio.intellij.android.wear-wff-schema",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.wear-dwf.tests",
    iml_files = ["intellij.android.wear-dwf.tests.iml"],
    test_class = "com.android.tools.idea.wear.dwf.AndroidWearDWFTestSuite",
    test_data = [
        "//tools/adt/idea/wear-dwf/testData",
    ],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:kotlin-test",
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
        "@intellij//:test-framework",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
    ],
    test_resources = ["testData"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "//tools/adt/idea/wear-dwf:intellij.android.wear-dwf",
        "//tools/base/wear-wff-schema:studio.intellij.android.wear-wff-schema",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/base/common:studio.android.sdktools.common",
    ],
)
