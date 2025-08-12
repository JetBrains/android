load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.wear-pairing",
    srcs = ["src"],
    iml_files = ["intellij.android.wear-pairing.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/wizard-model:intellij.android.wizard.model",
        "//tools/adt/idea/observable:intellij.android.observable",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib",
        "//tools/base/sdklib:studio.android.sdktools.sdklib",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/android-adb:intellij.android.adb",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/adt/idea/wizard:intellij.android.wizard",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/base/flags:studio.android.sdktools.flags",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.wear-pairing.tests",
    iml_files = ["intellij.android.wear-pairing.tests.iml"],
    test_class = "com.android.tools.idea.wearpairing.WearPairingTestSuite",
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "@intellij//:intellij-sdk",
        "//tools/adt/idea/wear-pairing:intellij.android.wear-pairing",
        "//tools/adt/idea/observable:intellij.android.observable",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/wizard-model:intellij.android.wizard.model",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/base/sdklib:studio.android.sdktools.sdklib",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test",
    ],
    test_friends = ["//tools/adt/idea/wear-pairing:intellij.android.wear-pairing"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = ["@intellij//:com.intellij.java"],
)
