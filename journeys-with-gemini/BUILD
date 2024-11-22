load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.journeys-with-gemini",
    srcs = ["src"],
    iml_files = ["intellij.android.journeys-with-gemini.iml"],
    kotlin_use_compose = True,
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/adt/idea/adt-ui-compose:intellij.android.adt.ui.compose[module]",
        "//tools/adt/idea/artwork-compose:intellij.android.artwork-compose[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
    ],
)
