load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:utils.bzl", "flat_archive")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.updater.ui",
    srcs = ["src"],
    iml_files = ["intellij.android.updater.ui.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-updater",
        "//tools/base/repository:studio.android.sdktools.repository[module]",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
    ],
)

java_binary(
    name = "patcher",
    main_class = "com.intellij.updater.Runner",
    stamp = 0,
    tags = [
        "block_network",
        "manual",
        "no_mac",
        "no_windows",
    ],
    visibility = ["//visibility:public"],
    runtime_deps = [
        ":intellij.android.updater.ui.jar",
        "//prebuilts/studio/intellij-sdk:studio-sdk-updater",
    ],
)

flat_archive(
    name = "sdk-patcher",
    ext = "zip",
    files = {
        ":source.properties": "sdk-patcher/source.properties",
        ":patcher_deploy.jar": "sdk-patcher/patcher.jar",
    },
    tags = [
        "manual",
        "no_mac",
        "no_windows",
    ],
)
