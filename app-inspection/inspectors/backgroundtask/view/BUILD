load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:jvm_import.bzl", "jvm_import")

# managed by go/iml_to_build
jvm_import(
    name = "background-inspector-proto",
    jars = ["//tools/base/app-inspection/inspectors/backgroundtask:backgroundtask_inspector_java_proto.jar"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
jvm_import(
    name = "workmanager-inspector-proto",
    jars = ["//prebuilts/tools/common/app-inspection/androidx/work:workmanager_inspector_proto.jar"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
iml_module(
    name = "app-inspection.inspectors.backgroundtask.view",
    srcs = ["src"],
    iml_files = ["app-inspection.inspectors.backgroundtask.view.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/.idea/libraries:studio-grpc",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//tools/adt/idea/app-inspection/inspectors/backgroundtask/view:background-inspector-proto",
        "//tools/adt/idea/app-inspection/inspectors/backgroundtask/view:workmanager-inspector-proto",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/app-inspection/inspector/api:app-inspection.inspector.api",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/app-inspection/inspectors/backgroundtask/model:app-inspection.inspectors.backgroundtask.model",
        "//tools/adt/idea/app-inspection/inspector/ide:app-inspection.inspector.ide",
        "//tools/adt/idea/inspectors-common/api:intellij.android.inspectors-common.api",
        "//tools/adt/idea/inspectors-common/ui:intellij.android.inspectors-common.ui",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model",
        "//tools/base/annotations:studio.android.sdktools.android-annotations",
        "//tools/adt/idea/codenavigation",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "app-inspection.inspectors.backgroundtask.view.tests",
    iml_files = ["app-inspection.inspectors.backgroundtask.view.tests.iml"],
    test_class = "com.android.tools.idea.appinspection.inspectors.backgroundtask.BackgroundTaskInspectorViewTestSuite",
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "@intellij//:intellij-sdk",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/app-inspection/inspectors/backgroundtask/model:app-inspection.inspectors.backgroundtask.model",
        "//tools/adt/idea/app-inspection/inspectors/backgroundtask/view:app-inspection.inspectors.backgroundtask.view",
        "//tools/adt/idea/app-inspection/inspector/api:app-inspection.inspector.api",
        "//tools/adt/idea/inspectors-common/api:intellij.android.inspectors-common.api",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model",
        "//tools/adt/idea/inspectors-common/ui:intellij.android.inspectors-common.ui",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/.idea/libraries:kotlin-test",
        "//tools/adt/idea/app-inspection/inspectors/backgroundtask/view:background-inspector-proto",
        "//tools/adt/idea/app-inspection/inspectors/backgroundtask/view:workmanager-inspector-proto",
        "//tools/adt/idea/app-inspection/inspectors/backgroundtask/ide:app-inspection.inspectors.backgroundtask.ide",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/codenavigation",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:com.intellij.java",
        "@intellij//:com.intellij.gradle",
    ],
)
