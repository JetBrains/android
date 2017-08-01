load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "uitest-framework",
    iml_files = ["uitest-framework.iml"],
    tags = ["managed"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/common:studio.common[module, test]",
        "//tools/adt/idea/android[module, test]",
        "//tools/idea/platform/platform-api[module, test]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools:fest-swing[module, test]",
        "//tools/idea/.idea/libraries:jsr305[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/plugins/gradle[module, test]",
        "//tools/idea/java/compiler/openapi:compiler-openapi[module, test]",
        "//tools/idea:java-impl[module, test]",
        "//tools/idea/java/openapi[module, test]",
        "//tools/idea/platform/testRunner[module, test]",
        "//tools/idea/platform/bootstrap[module, test]",
        "//tools/adt/idea/android/lib:spantable[test]",
        "//tools/adt/idea/adt-ui[module, test]",
        "//tools/adt/idea/designer[module, test]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/adt/idea/android-test-framework[module, test]",
        "//tools/studio/google/cloud/testing/test-recorder[module, test]",
        "//tools/base/flags:studio.flags[module, test]",
    ],
)
