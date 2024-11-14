load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "app-inspection.inspectors.backgroundtask.ide",
    srcs = ["src"],
    iml_files = ["app-inspection.inspectors.backgroundtask.ide.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/app-inspection/inspector/ide:app-inspection.inspector.ide[module]",
        "//tools/adt/idea/app-inspection/inspector/api:app-inspection.inspector.api[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/adt/idea/app-inspection/inspectors/backgroundtask/view:app-inspection.inspectors.backgroundtask.view[module]",
        "//tools/adt/idea/app-inspection/inspectors/backgroundtask/model:app-inspection.inspectors.backgroundtask.model[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/inspectors-common/api:intellij.android.inspectors-common.api[module]",
        "//tools/adt/idea/inspectors-common/ui:intellij.android.inspectors-common.ui[module]",
        "//tools/adt/idea/native-symbolizer[module]",
        "//tools/adt/idea/inspectors-common/api-ide:intellij.android.inspectors-common.api-ide[module]",
        "//tools/adt/idea/codenavigation[module]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
    ],
)
