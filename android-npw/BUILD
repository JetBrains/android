load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.newProjectWizard",
    srcs = ["src"],
    iml_files = ["intellij.android.newProjectWizard.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.newProjectWizard.tests",
    iml_files = ["intellij.android.newProjectWizard.tests.iml"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/android-npw:intellij.android.newProjectWizard[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module, test]",
    ],
)
