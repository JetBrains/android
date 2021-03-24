load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.app-inspection.inspectors.workmanager.ide",
    srcs = ["src"],
    iml_files = ["intellij.android.app-inspection.inspectors.workmanager.ide.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/app-inspection/inspectors/workmanager/model:intellij.android.app-inspection.inspectors.workmanager.model[module]",
        "//tools/adt/idea/app-inspection/inspector/api:intellij.android.app-inspection.inspector.api[module]",
        "//tools/adt/idea/app-inspection/inspector/ide:intellij.android.app-inspection.inspector.ide[module]",
        "//tools/adt/idea/app-inspection/inspectors/workmanager/view:intellij.android.app-inspection.inspectors.workmanager.view[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
    ],
)
