load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.deploy",
    srcs = ["src"],
    iml_files = ["intellij.android.deploy.iml"],
    resources = ["resources"],
    test_data = glob(["testData/**"]) + ["//prebuilts/studio/sdk:platform-tools"],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib",
        "//tools/base/deploy/deployer:studio.android.sdktools.deployer",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/base/tracer:studio.android.sdktools.tracer",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/build-common:intellij.android.buildCommon",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/base/adblib:studio.android.sdktools.adblib",
        "//tools/adt/idea/android-adb:intellij.android.adb",
    ],
)
