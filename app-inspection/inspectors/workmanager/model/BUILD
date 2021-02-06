load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.app-inspection.inspectors.workmanager.model",
    srcs = ["src"],
    iml_files = ["intellij.android.app-inspection.inspectors.workmanager.model.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    test_class = "com.android.tools.idea.appinspection.inspectors.workmanager.model.WorkManagerInspectorModelTestSuite",
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/app-inspection/inspector/api:intellij.android.app-inspection.inspector.api[module]",
        "//tools/adt/idea/observable:intellij.android.observable[module]",
        "//tools/adt/idea/.idea/libraries:studio-grpc",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//tools/adt/idea/app-inspection/inspectors/workmanager/model:workmanager-inspector-proto",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
    ],
)

# managed by go/iml_to_build
java_import(
    name = "workmanager-inspector-proto",
    jars = ["//prebuilts/tools/common/app-inspection/androidx/work:workmanager_inspector_proto.jar"],
    visibility = ["//visibility:public"],
)
