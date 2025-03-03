load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:jvm_import.bzl", "jvm_import")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.app-quality-insights.play-vitals.model",
    srcs = ["src"],
    iml_files = ["intellij.android.app-quality-insights.play-vitals.model.iml"],
    test_class = "com.android.tools.idea.vitals.PlayVitalsModelTestSuite",
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/app-quality-insights/api:intellij.android.app-quality-insights.api",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/app-quality-insights/play-vitals/model:libplay_vitals_java_proto",
        "//tools/studio/google/cloud/tools/google-login-plugin:google-login-as",
        "//tools/base/annotations:studio.android.sdktools.android-annotations",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[test]",
        "//tools/base/studio-grpc-testutils:studio.studio-grpc-testutils[test]",
        "//tools/adt/idea/app-quality-insights/play-vitals/model:libgemini_java_proto",
        "@intellij//:intellij.libraries.grpc",
        "@intellij//:intellij.libraries.grpc.netty.shaded",
    ],
)

# managed by go/iml_to_build
jvm_import(
    name = "libgemini_java_proto",
    jars = ["//tools/adt/idea/app-quality-insights/api/proto:libgemini_java_proto.jar"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
jvm_import(
    name = "libplay_vitals_java_proto",
    jars = ["//tools/adt/idea/app-quality-insights/play-vitals/model/proto:libplay_vitals_java_proto.jar"],
    visibility = ["//visibility:public"],
)
