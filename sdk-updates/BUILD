load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "sdk-updates",
    srcs = ["src"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/adt/idea/android[module]",
        "//tools/idea/platform/lang-api[module]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/analytics-library/protos:analytics-protos[module]",
        "//tools/adt/idea/observable[module]",
        "//tools/adt/idea/adt-ui[module]",
        "//tools/adt/idea/wizard[module]",
    ],
)

iml_module(
    name = "sdk-updates-tests",
    tags = ["managed"],
    test_srcs = ["testSrc"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/sdk-updates[module, test]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/base/repository:studio.repository[module, test]",
        "//tools/base/sdklib:studio.sdklib[module, test]",
        "//tools/adt/idea/android[module, test]",
    ],
)
