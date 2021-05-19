load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "analytics",
    srcs = ["src"],
    iml_files = ["analytics.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/.idea/libraries:studio-analytics-proto",
        "//tools/idea/.idea/libraries:HdrHistogram",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:protobuf",
        "//tools/idea/platform/core-api:intellij.platform.core[module]",
    ],
)

# managed by go/iml_to_build [unb]
iml_module(
    name = "analytics",
    srcs = ["src"],
    iml_files = ["analytics.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    project = "unb",
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:unb.kotlin-stdlib-jdk8",
        "//tools/adt/idea/.idea/libraries:unb.studio-analytics-proto",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/adt/idea/.idea/libraries:unb.protobuf",
    ],
)
