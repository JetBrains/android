load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "sdk-updates-tests",
    tags = ["managed"],
    test_srcs = ["testSrc"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/sdk-updates:sdk-updates[module, test]",
        "//tools/adt/idea:android[module, test]",
        "//tools:idea.platform-impl_and_others[module, test]",
    ],
)
