load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:coverage.bzl", "coverage_java_test")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.updater.studio-updater",
    srcs = ["src"],
    iml_files = ["intellij.android.updater.studio-updater.iml"],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "@intellij//:test-framework",
    ],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],  # Tested via the integration_test target below
    visibility = ["//visibility:public"],
    runtime_deps = ["//tools/adt/idea/.idea/libraries:opentest4j"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:updater",
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/analytics-library/shared:analytics-shared",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
    ],
)

coverage_java_test(
    name = "integration_test",
    data = ["//tools/adt/idea/studio:updater_deploy.jar"],
    tags = ["noci:studio-win"],  # b/77288863
    test_class = "com.android.studio.updater.StudioPatchUpdaterIntegrationTest",
    runtime_deps = [":intellij.android.updater.studio-updater_testlib"],
)
