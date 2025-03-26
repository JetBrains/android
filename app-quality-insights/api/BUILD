load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:jvm_import.bzl", "jvm_import")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.app-quality-insights.api",
    srcs = ["src"],
    iml_files = ["intellij.android.app-quality-insights.api.iml"],
    resources = ["resources"],
    test_class = "com.android.tools.idea.insights.InsightsApiTestSuite",
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "@intellij//:test-framework",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/base/studio-grpc-testutils:studio.studio-grpc-testutils",
        "@intellij//:intellij.platform.vcs.impl",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:org.jetbrains.kotlin",
        "@intellij//:org.intellij.groovy",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/studio/google/cloud/tools/google-login-plugin:google-login-as",
        "//tools/adt/idea/.idea/libraries:gradle-shared-proto",
        "//tools/adt/idea/.idea/libraries:google-api-client",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common",
        "//tools/adt/idea/app-quality-insights/api:libtitan_java_proto",
        "//tools/adt/idea/app-quality-insights/api:libgemini_java_proto",
        "//tools/adt/idea/app-quality-insights/api:libandroid-core-proto",
        "//tools/adt/idea/server-flags:intellij.android.server-flags",
        "//tools/adt/idea/ml-api:intellij.android.ml-api",
        "@intellij//:intellij.platform.vcs.dvcs.impl",
        "@intellij//:intellij.libraries.grpc",
        "@intellij//:intellij.libraries.grpc.netty.shaded",
    ],
)

# managed by go/iml_to_build
jvm_import(
    name = "libandroid-core-proto",
    jars = ["//tools/adt/idea/android:libandroid-core-proto.jar"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
jvm_import(
    name = "libtitan_java_proto",
    jars = ["//tools/adt/idea/app-quality-insights/api/proto:libtitan_java_proto.jar"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
jvm_import(
    name = "libgemini_java_proto",
    jars = ["//tools/adt/idea/app-quality-insights/api/proto:libgemini_java_proto.jar"],
    visibility = ["//visibility:public"],
)
