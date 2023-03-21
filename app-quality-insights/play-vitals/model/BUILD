load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.app-quality-insights.play-vitals.model",
    srcs = ["src"],
    iml_files = ["intellij.android.app-quality-insights.play-vitals.model.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/app-quality-insights/api:intellij.android.app-quality-insights.api[module]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/.idea/libraries:studio-grpc",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//tools/adt/idea/app-quality-insights/play-vitals/model:play_vitals_java_proto",
    ],
)

load("//tools/base/bazel:jvm_import.bzl", "jvm_import")

# managed by go/iml_to_build
jvm_import(
    name = "play_vitals_java_proto",
    jars = ["//tools/adt/idea/app-quality-insights/play-vitals/model/proto:play_vitals_java_proto.jar"],
    visibility = ["//visibility:public"],
)
