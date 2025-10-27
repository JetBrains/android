load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "glasses-pairing",
    srcs = ["src"],
    iml_files = ["glasses-pairing.iml"],
    kotlin_use_compose = True,
    resources = ["resources"],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "@intellij//:test-framework",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/adt/idea/adt-ui-compose:intellij.android.adt.ui.compose.tests",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/base/adblib:studio.android.sdktools.adblib",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/base/sdklib:studio.android.sdktools.sdklib",
        "//tools/adt/idea/android-adb:intellij.android.adb",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/observable:intellij.android.observable",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "@intellij//:com.intellij.java",
        "@intellij//:org.jetbrains.kotlin",
        "@intellij//:intellij-sdk",
        "//tools/adt/idea/adt-ui-compose:intellij.android.adt.ui.compose",
        "//tools/adt/idea/artwork-compose:intellij.android.artwork-compose",
        "//tools/base/adblib-tools:studio.android.sdktools.adblib.tools",
    ],
)
