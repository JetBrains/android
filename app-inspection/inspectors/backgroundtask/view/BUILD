load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.app-inspection.inspectors.backgroundtask.view",
    srcs = ["src"],
    iml_files = ["intellij.android.app-inspection.inspectors.backgroundtask.view.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:studio-grpc",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//tools/adt/idea/app-inspection/inspectors/backgroundtask/view:background-inspector-proto",
        "//tools/adt/idea/app-inspection/inspectors/backgroundtask/view:workmanager-inspector-proto",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/adt/idea/app-inspection/inspector/api:intellij.android.app-inspection.inspector.api[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/app-inspection/inspectors/backgroundtask/model:intellij.android.app-inspection.inspectors.backgroundtask.model[module]",
        "//tools/adt/idea/app-inspection/inspector/ide:intellij.android.app-inspection.inspector.ide[module]",
        "//tools/adt/idea/inspectors-common/api:intellij.android.inspectors-common.api[module]",
        "//tools/adt/idea/inspectors-common/ui:intellij.android.inspectors-common.ui[module]",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module]",
    ],
)

# managed by go/iml_to_build
java_import(
    name = "background-inspector-proto",
    jars = ["//tools/base/app-inspection/inspectors/backgroundtask:backgroundtask_inspector_java_proto.jar"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
java_import(
    name = "workmanager-inspector-proto",
    jars = ["//prebuilts/tools/common/app-inspection/androidx/work:workmanager_inspector_proto.jar"],
    visibility = ["//visibility:public"],
)
