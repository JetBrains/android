load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.studio-labs",
    srcs = ["src"],
    iml_files = ["intellij.android.studio-labs.iml"],
    kotlin_use_compose = True,
    resources = ["resources"],
    # do not sort: must match IML order
    test_deps = [
        "@intellij//:test-framework",
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/.idea/libraries:truth-java8-extension",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/adt/idea/adt-ui-compose:intellij.android.adt.ui.compose",
        "@intellij//:intellij-sdk",
    ],
)
