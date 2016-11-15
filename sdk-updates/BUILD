load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "sdk-updates",
    srcs = ["src"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea:android[module]",
        "//tools:idea.lang-api[module]",
        "//tools/analytics-library:analytics-tracker[module]",
        "//tools/analytics-library:analytics-protos[module]",
        "//tools:idea.platform-impl_and_others[module]",
        "//tools/adt/idea/observable:observable[module]",
        "//tools/adt/idea/adt-ui:adt-ui[module]",
    ],
)
