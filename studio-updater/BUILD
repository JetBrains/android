load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:utils.bzl", "select_project")
load("//tools/base/bazel:coverage.bzl", "coverage_java_test")

# managed by go/iml_to_build [unb]
iml_module(
    name = "intellij.android.updater.studio-updater",
    srcs = ["src"],
    iml_files = ["intellij.android.updater.studio-updater.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    project = "unb",
    test_srcs = ["testSrc"],
    test_tags = ["manual"],  # Tested via the integration_test target below
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-updater",
        "//tools/adt/idea/.idea/libraries:unb.assertJ[test]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/analytics-library/shared:analytics-shared[module]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/adt/idea/.idea/libraries:unb.studio-analytics-proto",
        "//tools/adt/idea/.idea/libraries:unb.protobuf",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/.idea/libraries:unb.Guava[test]",
        "//tools/adt/idea/.idea/libraries:unb.kotlin-stdlib-jdk8",
    ],
)

coverage_java_test(
    name = "integration_test",
    data = select_project({
        "": ["//tools/idea/updater:updater_deploy.jar"],
        "unb": ["//tools/adt/idea/studio:updater_deploy.jar"],
    }),
    jvm_flags = select_project({
        "": ["-Dupdater.jar.path=tools/idea/updater/updater_deploy.jar"],
        "unb": [],
    }),
    tags = ["no_test_windows"],  # b/77288863
    test_class = "com.android.studio.updater.StudioPatchUpdaterIntegrationTest",
    runtime_deps = [":intellij.android.updater.studio-updater_testlib"],
)
