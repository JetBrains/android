load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.rt",
    srcs = ["src"],
    iml_files = ["intellij.android.rt.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:jetbrains-annotations-java5",
        "//tools/idea/platform/util-rt:intellij.platform.util.rt[module]",
    ],
)
