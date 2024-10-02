load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:jvm_import.bzl", "jvm_import")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.app-quality-insights.api",
    srcs = ["src"],
    iml_files = ["intellij.android.app-quality-insights.api.iml"],
    test_class = "com.android.tools.idea.insights.InsightsApiTestSuite",
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:org.jetbrains.kotlin",
        "@intellij//:org.intellij.groovy",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "@intellij//:test-framework[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/studio/google/cloud/tools/google-login-plugin:google-login-as[module]",
        "//tools/adt/idea/.idea/libraries:studio-grpc",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//tools/adt/idea/.idea/libraries:gradle-shared-proto",
        "//tools/adt/idea/.idea/libraries:google-api-client",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
        "//tools/adt/idea/app-quality-insights/api:titan_java_proto",
        "//tools/adt/idea/app-quality-insights/api:gemini_java_proto",
        "//tools/base/studio-grpc-testutils:studio.studio-grpc-testutils[module, test]",
        "//tools/adt/idea/app-quality-insights/api:libandroid-core-proto",
        "//tools/adt/idea/server-flags:intellij.android.server-flags[module]",
        "//tools/adt/idea/ml-api:intellij.android.ml-api[module]",
        "@intellij//:intellij.platform.vcs.dvcs.impl",
        "@intellij//:intellij.platform.vcs.impl[test]",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin[test]",
    ],
)

# managed by go/iml_to_build
jvm_import(
    name = "titan_java_proto",
    jars = ["//tools/adt/idea/app-quality-insights/api/proto:titan_java_proto.jar"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
jvm_import(
    name = "libandroid-core-proto",
    jars = ["//tools/adt/idea/android:libandroid-core-proto.jar"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
jvm_import(
    name = "gemini_java_proto",
    jars = ["//tools/adt/idea/app-quality-insights/api/proto:gemini_java_proto.jar"],
    visibility = ["//visibility:public"],
)
