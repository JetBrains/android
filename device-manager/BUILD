load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.device-manager",
    srcs = ["src"],
    iml_files = ["intellij.android.device-manager.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/base/adblib:studio.android.sdktools.adblib[module]",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/base/sdklib:studio.android.sdktools.sdklib[module]",
        "//tools/adt/idea/android-adb:intellij.android.adb[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/intellij.android.device[module]",
        "//tools/adt/idea/device-explorer:intellij.android.device-explorer[module]",
        "//tools/adt/idea/device-file-explorer-toolwindow:intellij.android.device-file-explorer-toolwindow[module]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
        "//tools/adt/idea/wear-pairing:intellij.android.wear-pairing[module]",
        "//tools/adt/idea/wizard:intellij.android.wizard[module]",
        "//tools/adt/idea/wizard-model:intellij.android.wizard.model[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.device-manager.tests",
    iml_files = ["intellij.android.device-manager.tests.iml"],
    test_class = "com.android.tools.idea.devicemanager.DeviceManagerTestSuite",
    # keep sorted
    test_data = [
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/testData",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto[test]",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner[module, test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/android-adb:intellij.android.adb[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module, test]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/intellij.android.device[module, test]",
        "//tools/adt/idea/device-manager:intellij.android.device-manager[module, test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/wear-pairing:intellij.android.wear-pairing[module, test]",
    ],
)
