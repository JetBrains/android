load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "uitest-framework",
    tags = ["managed"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/common:studio.common[module, test]",
        "//tools/adt/idea:android[module, test]",
        "//tools:idea.platform-api[module, test]",
        "//tools:idea.platform-impl_and_others[module]",
        "//tools:fest-swing[module, test]",
        "//tools/idea/.idea/libraries:jsr305[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools:idea.gradle[module, test]",
        "//tools:idea.compiler-openapi[module, test]",
        "//tools:idea.java-impl[module, test]",
        "//tools:idea.openapi[module, test]",
        "//tools:idea.testRunner[module, test]",
        "//tools:idea.bootstrap[module, test]",
        "//tools/adt/idea/android/lib:spantable[test]",
        "//tools/adt/idea/adt-ui[module, test]",
        "//tools/base/rpclib:studio.rpclib[module, test]",
        "//tools/adt/idea/designer[module, test]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/adt/idea/android-test-framework[module, test]",
    ],
)
