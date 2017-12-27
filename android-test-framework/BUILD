load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "android-test-framework",
    iml_files = ["android-test-framework.iml"],
    tags = ["managed"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea/xml/xml-psi-api[module, test]",
        "//tools/idea:compiler-impl_and_others[module]",
        "//tools/adt/idea/android[module, test]",
        "//tools/adt/idea/android-common[module, test]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/.idea/libraries:jsr305[test]",
        "//tools/adt/idea/adt-ui[module, test]",
        "//tools/idea/.idea/libraries:mockito[test]",
    ],
)
