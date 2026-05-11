load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:jvm_import.bzl", "jvm_import")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.play-publishing",
    srcs = ["src"],
    iml_files = ["intellij.android.play-publishing.iml"],
    kotlin_use_compose = True,
    resources = ["resources"],
    test_class = "com.android.tools.idea.publishing.play.PlayPublishingTestSuite",
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/adt-ui-compose:intellij.android.adt.ui.compose.tests",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "@intellij//:test-framework",
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/base/testutils:studio.android.sdktools.testutils",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/adt-ui-compose:intellij.android.adt.ui.compose",
        "//tools/adt/idea/artwork-compose:intellij.android.artwork-compose",
        "//tools/vendor/google/login:google-login",
        "@intellij//:intellij.libraries.grpc",
        "@intellij//:intellij.libraries.grpc.netty.shaded",
        "//tools/adt/idea/.idea/libraries:protobuf-kotlin",
        "//tools/adt/idea/play-publishing:play_publishing_kt_proto",
        "//tools/adt/idea/app-publishing:intellij.android.app-publishing",
        "//tools/adt/idea/.idea/libraries:aapt-proto",
        "//tools/base/apkparser/analyzer:studio.android.sdktools.analyzer",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common",
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/adt/idea/apkanalyzer:intellij.android.apkanalyzer",
        "//tools/adt/idea/.idea/libraries:libbinary-resources",
        "//tools/adt/idea/artwork:intellij.android.artwork",
    ],
)

# managed by go/iml_to_build
jvm_import(
    name = "play_publishing_kt_proto",
    jars = ["//tools/adt/idea/play-publishing/proto:play_publishing_kt_proto.jar"],
    visibility = ["//visibility:public"],
)
