load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "layoutlib",
    srcs = ["src"],
    bundle_data = [
        "//prebuilts/studio/layoutlib:data/res",
        "//prebuilts/studio/layoutlib:data/fonts",
        "//prebuilts/studio/layoutlib:buildprop",
    ],
    iml_files = ["layoutlib.iml"],
    resources = ["resources"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:xml-structure-view-impl",
        "//tools/idea:spellchecker",
        "//tools/idea:images",
    ],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/.idea/libraries:layoutlib"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:RegExpSupport",
        "//tools/idea:xml-analysis-impl",
        "//tools/idea:xdebugger-impl",
        "//tools/idea:lvcs-impl",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:built-in-server",
        "//tools/idea:tasks-platform-impl",
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea:dom-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:layoutlib",
        "//tools/base/sdk-common:studio.sdk-common[module]",
        "//tools/idea/platform/annotations[module]",
        "//tools/adt/idea/android-common[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/base/layoutlib-api:studio.layoutlib-api[module]",
        "//tools/idea/.idea/libraries:ASM",
    ],
)
