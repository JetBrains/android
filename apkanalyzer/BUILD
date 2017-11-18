load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "apkanalyzer",
    srcs = ["src"],
    iml_files = ["apkanalyzer.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:testExtensions",
        "//tools/idea:external-system-impl",
        "//tools/idea:execution-impl",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea:lvcs-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/editor-ui-api[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea:platform-impl[module]",
        "//tools/base/apkparser/analyzer:studio.analyzer[module]",
        "//tools/base/common:studio.common[module]",
        "//tools/base/apkparser:studio.binary-resources[module]",
        "//tools/adt/idea/adt-ui[module]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/analytics-library/protos:analytics-protos[module]",
        "//tools/adt/idea/android-common[module]",
        "//tools/adt/idea/artwork[module]",
        "//tools/base/sdk-common:studio.sdk-common[module]",
        "//tools/idea/.idea/libraries:dexlib2",
        "//tools/idea:compiler-impl_and_others[module]",
        "//tools/adt/idea/project-system[module]",
        "//tools/adt/idea/smali[module]",
        "//tools/adt/idea/android-lang[module]",
    ],
)
