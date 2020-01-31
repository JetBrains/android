load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.adt.ui.model",
    srcs = ["src/main/java"],
    iml_files = ["intellij.android.adt.ui.model.iml"],
    lint_baseline = "lint_baseline.xml",
    test_coverage = True,
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:jetbrains-annotations-java5",
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools/idea/platform/platform-api:intellij.platform.ide[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
    ],
)
