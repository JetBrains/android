load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "app-inspection.api",
    srcs = ["src"],
    iml_files = ["app-inspection.api.iml"],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver",
        "//tools/adt/idea/.idea/libraries:mockito",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/android-transport:intellij.android.transport",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/.idea/libraries:transport-proto",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//tools/adt/idea/.idea/libraries:studio-grpc",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/base/annotations:studio.android.sdktools.android-annotations",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib",
        "//tools/adt/idea/app-inspection/inspector/api:app-inspection.inspector.api",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common",
    ],
)
