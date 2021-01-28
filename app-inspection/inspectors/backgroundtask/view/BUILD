load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.app-inspection.inspectors.backgroundtask.view",
    srcs = ["src"],
    iml_files = ["intellij.android.app-inspection.inspectors.backgroundtask.view.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/app-inspection/inspector/ide:intellij.android.app-inspection.inspector.ide[module]",
        "//tools/adt/idea/app-inspection/inspectors/backgroundtask/model:intellij.android.app-inspection.inspectors.backgroundtask.model[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/app-inspection/inspectors/backgroundtask/view:background-inspector-proto",
    ],
)

# managed by go/iml_to_build
java_import(
    name = "background-inspector-proto",
    jars = ["//tools/base/app-inspection/inspectors/backgroundtask:backgroundtask_inspector_java_proto.jar"],
    visibility = ["//visibility:public"],
)
