load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.gradle-tooling.impl",
    srcs = ["src"],
    iml_files = ["intellij.android.gradle-tooling.impl.iml"],
    resources = ["resources"],
    tags = ["no_test_windows"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:Gradle",
        "//tools/idea/.idea/libraries:javax-inject",
        "//tools/adt/idea/gradle-tooling/studio-gradle-tooling-api:intellij.android.gradle-tooling.api[module]",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:equalsverifier[test]",
    ],
)
