load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.compose-ide-plugin",
    srcs = ["src/main/java"],
    iml_files = ["intellij.android.compose-ide-plugin.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    resources = ["src/main/resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/adt/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
    ],
)
