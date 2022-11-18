load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "app-inspection.ide",
    srcs = ["src"],
    iml_files = ["app-inspection.ide.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    resources = ["resources"],
    test_resources = ["testData"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/app-inspection/api:app-inspection.api[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/android-transport:intellij.android.transport[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//tools/adt/idea/.idea/libraries:transport-proto",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/adt/idea/deploy:intellij.android.deploy[module]",
        "//tools/adt/idea/app-inspection/inspector/ide:app-inspection.inspector.ide[module]",
        "//tools/adt/idea/app-inspection/inspector/api:app-inspection.inspector.api[module]",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
        "//tools/base/threading-agent-callback:studio.android.sdktools.threading-agent-callback[module]",
        "//tools/adt/idea/execution/common:intellij.android.execution.common[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "app-inspection.ide.tests",
    iml_files = ["app-inspection.ide.tests.iml"],
    test_class = "com.android.tools.idea.appinspection.ide.AppInspectionIdeTestSuite",
    # keep sorted
    test_data = glob(["testData/**"]),
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:studio-grpc[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module, test]",
        "//tools/adt/idea/app-inspection/api:app-inspection.api[module, test]",
        "//tools/adt/idea/app-inspection/ide:app-inspection.ide[module, test]",
        "//tools/adt/idea/app-inspection/inspector/api:app-inspection.inspector.api[module, test]",
        "//tools/adt/idea/app-inspection/inspector/ide:app-inspection.inspector.ide[module, test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module, test]",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module, test]",
        "//tools/adt/idea/android-transport:intellij.android.transport[module, test]",
        "//tools/adt/idea/.idea/libraries:studio-proto[test]",
        "//tools/adt/idea/.idea/libraries:transport-proto[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/sdk-updates:intellij.android.sdkUpdates[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module, test]",
        "//tools/adt/idea/deploy:intellij.android.deploy[module, test]",
        "//tools/adt/idea/execution/common:intellij.android.execution.common[module, test]",
    ],
)
