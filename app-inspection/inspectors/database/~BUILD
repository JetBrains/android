load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "app-inspection.inspectors.database",
    srcs = ["src"],
    iml_files = ["app-inspection.inspectors.database.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android-adb:intellij.android.adb",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/base/annotations:studio.android.sdktools.android-annotations",
        "//tools/adt/idea/android-lang:intellij.android.lang",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//tools/adt/idea/android-transport:intellij.android.transport",
        "//tools/adt/idea/.idea/libraries:sqlite-inspector-proto",
        "//tools/adt/idea/app-inspection/inspector/api:app-inspection.inspector.api",
        "//tools/adt/idea/app-inspection/inspector/ide:app-inspection.inspector.ide",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/device-explorer-files:intellij.android.device-explorer-files",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "app-inspection.inspectors.database.tests",
    iml_files = ["app-inspection.inspectors.database.tests.iml"],
    split_test_targets = {
        "ExportToFileDialogTest": {
            "tags": ["noci:studio-win"],
            "test_filter": "com.android.tools.idea.sqlite.ui.ExportToFileDialogTest",
        },
        "other": {},
    },
    test_class = "com.android.tools.idea.DatabaseInspectorTestSuite",
    test_data = ["//prebuilts/studio/sdk:platform-tools"],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/app-inspection/inspectors/database:app-inspection.inspectors.database",
        "//tools/adt/idea/android-adb:intellij.android.adb",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib",
        "//tools/adt/idea/device-explorer-files:intellij.android.device-explorer-files",
        "//tools/adt/idea/.idea/libraries:kotlin-test",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
    ],
    test_srcs = ["testSrc"],
    test_tags = [],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    runtime_deps = ["//tools/adt/idea/.idea/libraries:sqlite"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:com.intellij.gradle",
        "//tools/adt/idea/android-lang:intellij.android.lang",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//tools/adt/idea/android-transport:intellij.android.transport",
        "//tools/adt/idea/.idea/libraries:sqlite-inspector-proto",
        "//tools/adt/idea/app-inspection/inspector/api:app-inspection.inspector.api",
        "//tools/base/annotations:studio.android.sdktools.android-annotations",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/app-inspection/inspector/ide:app-inspection.inspector.ide",
        "//tools/adt/idea/artwork:intellij.android.artwork",
    ],
)
