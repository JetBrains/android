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
    name = "app-inspection.inspectors.backgroundtask.model",
    srcs = ["src"],
    iml_files = ["app-inspection.inspectors.backgroundtask.model.iml"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/app-inspection/api:app-inspection.api",
        "//tools/adt/idea/app-inspection/inspector/api:app-inspection.inspector.api",
        "//tools/adt/idea/.idea/libraries:studio-grpc",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//tools/adt/idea/app-inspection/inspectors/backgroundtask/model:background-inspector-proto",
        "//tools/adt/idea/app-inspection/inspectors/backgroundtask/model:workmanager-inspector-proto",
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/inspectors-common/api:intellij.android.inspectors-common.api",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/codenavigation",
    ],
)
