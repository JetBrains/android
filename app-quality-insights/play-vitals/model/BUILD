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
        "//tools/adt/idea/app-quality-insights/api:intellij.android.app-quality-insights.api[module]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/.idea/libraries:studio-grpc",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//tools/adt/idea/app-quality-insights/play-vitals/model:play_vitals_java_proto",
        "//tools/studio/google/cloud/tools/google-login-plugin:google-login-as[module]",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/base/studio-grpc-testutils:studio.studio-grpc-testutils[module, test]",
    ],
)

# managed by go/iml_to_build
jvm_import(
    name = "play_vitals_java_proto",
    jars = ["//tools/adt/idea/app-quality-insights/play-vitals/model/proto:play_vitals_java_proto.jar"],
    visibility = ["//visibility:public"],
)
