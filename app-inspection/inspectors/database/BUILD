load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "app-inspection.inspectors.database",
    srcs = ["src"],
    iml_files = ["app-inspection.inspectors.database.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/android-adb:intellij.android.adb[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/adt/idea/android-lang:intellij.android.lang[module]",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//tools/adt/idea/android-transport:intellij.android.transport[module]",
        "//tools/adt/idea/.idea/libraries:sqlite-inspector-proto",
        "//tools/adt/idea/.idea/libraries:kotlinx-coroutines-guava",
        "//tools/adt/idea/app-inspection/inspector/api:app-inspection.inspector.api[module]",
        "//tools/adt/idea/app-inspection/inspector/ide:app-inspection.inspector.ide[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
        "//tools/adt/idea/device-file-explorer-toolwindow:intellij.android.device-file-explorer-toolwindow[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "app-inspection.inspectors.database.tests",
    iml_files = ["app-inspection.inspectors.database.tests.iml"],
    split_test_targets = {
        "ExportToFileDialogTest": {
            "tags": ["no_windows"],
            "test_filter": "com.android.tools.idea.sqlite.ui.ExportToFileDialogTest",
        },
        "other": {
            # b/182573662, b/217469630
            "tags": ["very_flaky"],
        },
    },
    test_class = "com.android.tools.idea.debuggers.AndroidDebuggersTestSuite",
    test_data = ["//prebuilts/studio/sdk:platform-tools"],
    test_srcs = ["testSrc"],
    test_tags = [],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    runtime_deps = ["//tools/adt/idea/.idea/libraries:sqlite"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle",
        "//tools/adt/idea/app-inspection/inspectors/database:app-inspection.inspectors.database[module, test]",
        "//tools/adt/idea/android-adb:intellij.android.adb[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/adt/idea/android-lang:intellij.android.lang[module]",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//tools/adt/idea/android-transport:intellij.android.transport[module]",
        "//tools/adt/idea/.idea/libraries:sqlite-inspector-proto",
        "//tools/adt/idea/.idea/libraries:kotlinx-coroutines-guava",
        "//tools/adt/idea/app-inspection/inspector/api:app-inspection.inspector.api[module]",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/app-inspection/inspector/ide:app-inspection.inspector.ide[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module, test]",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module, test]",
        "//tools/adt/idea/device-file-explorer-toolwindow:intellij.android.device-file-explorer-toolwindow[module, test]",
    ],
)
