load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "designer",
    srcs = ["src"],
    resources = ["resources"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools:idea.editor-ui-api[module]",
        "//tools:idea.core-api[module]",
        "//tools:idea.platform-api[module]",
        "//tools/adt/idea:android[module]",
        "//tools/sherpa:sherpa-solver[module]",
        "//tools/adt/idea:sherpa-ui[module]",
        "//tools:idea.xml-psi-api[module]",
        "//tools:idea.platform-impl_and_others[module]",
        "//tools/base/pixelprobe:studio.pixelprobe[module]",
        "//tools/base/annotations:studio.android-annotations[module]",
        "//tools/adt/idea/android/lib:jsr305-1.3.9",
        "//tools:idea.lang-api[module]",
        "//tools/idea/.idea/libraries:layoutlib",
        "//tools:idea.java-psi-impl[module]",
        "//tools:idea.java-indexing-api[module]",
        "//tools/base/asset-studio:studio.assetstudio[module]",
        "//tools/adt/idea/adt-ui[module]",
    ],
)
