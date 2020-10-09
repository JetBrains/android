load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build [unb]
iml_module(
    name = "intellij.android.app-inspection.inspectors.workmanager.view",
    srcs = ["src"],
    bundled_deps = ["//tools/adt/idea/app-inspection/inspectors/workmanager/view:unb.workmanager-inspector-proto"],
    iml_files = ["intellij.android.app-inspection.inspectors.workmanager.view.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    project = "unb",
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:unb.kotlin-stdlib-jdk8",
        "//tools/adt/idea/app-inspection/inspectors/workmanager/model:intellij.android.app-inspection.inspectors.workmanager.model[module]",
        "//tools/adt/idea/app-inspection/inspector/api:intellij.android.app-inspection.inspector.api[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/adt/idea/.idea/libraries:unb.studio-grpc",
        "//tools/adt/idea/.idea/libraries:unb.studio-proto",
        "//tools/adt/idea/app-inspection/inspectors/workmanager/view:unb.workmanager-inspector-proto",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
    ],
)

# managed by go/iml_to_build [unb]
java_import(
    name = "unb.workmanager-inspector-proto",
    jars = ["//prebuilts/tools/common/app-inspection/androidx/work:workmanager_inspector_proto.jar"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build [unb]
iml_module(
    name = "intellij.android.app-inspection.inspectors.workmanager.view.tests",
    iml_files = ["intellij.android.app-inspection.inspectors.workmanager.view.tests.iml"],
    project = "unb",
    test_class = "com.android.tools.idea.appinspection.inspectors.workmanager.view.WorkManagerInspectorViewTestSuite",
    test_flaky = True,  # TODO(b/169760203) fix test failures on Windows
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk[test]",
        "//tools/adt/idea/.idea/libraries:unb.kotlin-stdlib-jdk8[test]",
        "//tools/adt/idea/app-inspection/inspectors/workmanager/model:intellij.android.app-inspection.inspectors.workmanager.model[module, test]",
        "//tools/adt/idea/app-inspection/inspector/api:intellij.android.app-inspection.inspector.api[module, test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module, test]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module, test]",
        "//tools/adt/idea/.idea/libraries:unb.studio-grpc[test]",
        "//tools/adt/idea/.idea/libraries:unb.studio-proto[test]",
        "//tools/adt/idea/app-inspection/inspectors/workmanager/view:unb.workmanager-inspector-proto[test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/.idea/libraries:unb.truth[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/app-inspection/inspectors/workmanager/view:intellij.android.app-inspection.inspectors.workmanager.view[module, test]",
        "//tools/adt/idea/.idea/libraries:unb.mockito[test]",
    ],
)
