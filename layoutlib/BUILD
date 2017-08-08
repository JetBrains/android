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
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/.idea/libraries:layoutlib"],
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
