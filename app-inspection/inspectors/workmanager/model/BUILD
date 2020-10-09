load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build [unb]
iml_module(
    name = "intellij.android.app-inspection.inspectors.workmanager.model",
    srcs = ["src"],
    bundled_deps = ["//tools/adt/idea/app-inspection/inspectors/workmanager/model:unb.workmanager-inspector-proto"],
    iml_files = ["intellij.android.app-inspection.inspectors.workmanager.model.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    project = "unb",
    test_class = "com.android.tools.idea.appinspection.inspectors.workmanager.model.WorkManagerInspectorModelTestSuite",
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:unb.kotlin-stdlib-jdk8",
        "//tools/adt/idea/app-inspection/inspector/api:intellij.android.app-inspection.inspector.api[module]",
        "//tools/adt/idea/.idea/libraries:unb.Guava",
        "//tools/adt/idea/observable:intellij.android.observable[module]",
        "//tools/adt/idea/.idea/libraries:unb.studio-grpc",
        "//tools/adt/idea/.idea/libraries:unb.studio-proto",
        "//tools/adt/idea/app-inspection/inspectors/workmanager/model:unb.workmanager-inspector-proto",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/.idea/libraries:unb.truth[test]",
    ],
)

# managed by go/iml_to_build [unb]
java_import(
    name = "unb.workmanager-inspector-proto",
    jars = ["//prebuilts/tools/common/app-inspection/androidx/work:workmanager_inspector_proto.jar"],
    visibility = ["//visibility:public"],
)
