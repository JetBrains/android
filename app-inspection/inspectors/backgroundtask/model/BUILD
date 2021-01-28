load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.app-inspection.inspectors.backgroundtask.model",
    srcs = ["src"],
    iml_files = ["intellij.android.app-inspection.inspectors.backgroundtask.model.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/app-inspection/api:intellij.android.app-inspection.api[module]",
        "//tools/adt/idea/app-inspection/inspector/api:intellij.android.app-inspection.inspector.api[module]",
        "//tools/adt/idea/.idea/libraries:studio-grpc",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//tools/adt/idea/app-inspection/inspectors/backgroundtask/model:background-inspector-proto",
    ],
)

# managed by go/iml_to_build
java_import(
    name = "background-inspector-proto",
    jars = ["//tools/base/app-inspection/inspectors/backgroundtask:backgroundtask_inspector_java_proto.jar"],
    visibility = ["//visibility:public"],
)
