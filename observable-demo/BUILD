load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "observable-demo",
    srcs = ["src"],
    iml_files = ["observable-demo.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/observable:intellij.android.observable[module]",
        "//tools/adt/idea/observable-ui:intellij.android.observable.ui[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
    ],
)
