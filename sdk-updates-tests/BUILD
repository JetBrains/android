load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "sdk-updates-tests",
    tags = ["managed"],
    test_srcs = ["testSrc"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/sdk-updates:sdk-updates[module, test]",
        "//tools:idea.platform-impl_and_others[module]",
        "//tools/base/repository:studio.repository[module, test]",
        "//tools/base/sdklib:studio.sdklib[module, test]",
        "//tools/adt/idea:android[module, test]",
    ],
)
