load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "adt-testutils",
    srcs = ["src/main/java"],
    iml_files = ["adt-testutils.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/annotations[module]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/base/testutils:studio.testutils[module]",
        "//tools/base/repository:studio.repository[module]",
        "//tools/idea/.idea/libraries:JUnit4",
        "//tools/idea/.idea/libraries:Gradle",
    ],
)
