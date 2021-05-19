load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.app-inspection.inspector.api",
    # do not sort: must match IML order
    srcs = ["src"],
    iml_files = ["intellij.android.app-inspection.inspector.api.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/idea/.idea/libraries:kotlinx-coroutines-jdk8",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/platform/util-ex:intellij.platform.util.ex[module]",
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
    ],
)

# managed by go/iml_to_build [unb]
iml_module(
    name = "intellij.android.app-inspection.inspector.api",
    srcs = ["src"],
    iml_files = ["intellij.android.app-inspection.inspector.api.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    project = "unb",
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:unb.kotlin-stdlib-jdk8",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/adt/idea/.idea/libraries:unb.truth[test]",
    ],
)
