load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:jvm_import.bzl", "jvm_import")

# managed by go/iml_to_build
jvm_import(
    name = "play_findings_proto",
    jars = ["//tools/adt/idea/app-quality-insights/play-findings/client/proto:play_findings_proto.jar"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.app-quality-insights.play-findings.client",
    iml_files = ["intellij.android.app-quality-insights.play-findings.client.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "//tools/adt/idea/app-quality-insights/play-findings/client:play_findings_proto",
        "//tools/adt/idea/.idea/libraries:protobuf-java-util",
    ],
)
