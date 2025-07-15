load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.app-quality-insights.play-policy.ide",
    srcs = ["src"],
    iml_files = ["intellij.android.app-quality-insights.play-policy.ide.iml"],
    resources = ["resources"],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/lint:intellij.lint",
    ],
)
