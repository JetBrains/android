load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.design-plugin",
    srcs = ["src"],
    iml_files = ["intellij.android.design-plugin.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/designer:intellij.android.designer[module]",
        "//tools/adt/idea/nav/editor:intellij.android.nav.editor[module]",
        "//tools/adt/idea/compose-designer:intellij.android.compose-designer[module]",
        "//tools/adt/idea/designer/customview:intellij.android.designer.customview[module]",
        "//tools/adt/idea/layoutlib:intellij.android.layoutlib[module]",
        "//tools/adt/idea/glance-designer:intellij.android.glance-designer[module]",
        "//tools/adt/idea/.idea/libraries:layoutlib",
    ],
)
