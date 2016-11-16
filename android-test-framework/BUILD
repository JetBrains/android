load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "android-test-framework",
    tags = ["managed"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools:idea.platform-impl_and_others[module]",
        "//tools:idea.xml-psi-api[module, test]",
        "//tools:idea.compiler-impl_and_others[module]",
        "//tools/adt/idea/android[module, test]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/android/lib:jsr305-1.3.9[test]",
        "//tools/adt/idea/adt-ui[module, test]",
    ],
)
