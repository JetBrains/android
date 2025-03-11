load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.avd",
    srcs = ["src"],
    iml_files = ["intellij.android.avd.iml"],
    kotlin_use_compose = True,
    test_class = "com.android.tools.idea.avd.AvdTestSuite",
    # do not sort: must match IML order
    test_deps = [
        "@intellij//:test-framework",
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/.idea/libraries:kotlin-test",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/adt/idea/intellij.android.device.tests",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "@intellij//:com.intellij.java",
        "@intellij//:intellij-sdk",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/base/adblib:studio.android.sdktools.adblib",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/base/sdklib:studio.android.sdktools.sdklib",
        "//tools/adt/idea/android-adb:intellij.android.adb",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/adt-ui-compose:intellij.android.adt.ui.compose",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/adt/idea/artwork-compose:intellij.android.artwork-compose",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/intellij.android.device",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
        "//tools/adt/idea/wizard:intellij.android.wizard",
        "//tools/adt/idea/wizard-model:intellij.android.wizard.model",
    ],
)
