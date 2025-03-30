load("//tools/base/bazel:proto.bzl", "android_java_proto_library")

package(default_visibility = ["//visibility:public"])

android_java_proto_library(
    name = "layout_inspector_snapshot_java_proto",
    srcs = ["snapshot.proto"],
    java_deps = [
        "//prebuilts/tools/common/app-inspection/androidx/compose/ui:layout_inspector_compose_java_proto-nojarjar",
        "//tools/base/dynamic-layout-inspector/agent/appinspection/proto:layout_inspector_view_java_proto-nojarjar",
    ],
    proto_deps = [
        "//prebuilts/tools/common/app-inspection/androidx/compose/ui:layout_inspector_compose_java_proto-proto",
        "//tools/base/dynamic-layout-inspector/agent/appinspection/proto:_layout_inspector_view_java_proto_internal_srcs",
    ],
)
