load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "adt-branding",
    srcs = ["src"],
    bundle_data = glob(["**"]),
    iml_files = ["adt-branding.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:testExtensions",
        "//tools/idea:external-system-impl",
        "//tools/idea:execution-impl",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:xdebugger-impl",
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea:lvcs-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/util[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea:lang-impl[module]",
        "//tools/idea:compiler-impl_and_others[module]",
        "//tools/idea:xml[module]",
    ],
)
