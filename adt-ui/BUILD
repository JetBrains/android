load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "adt-ui",
    srcs = ["src/main/java"],
    iml_files = ["adt-ui.iml"],
    resources = ["resources"],
    tags = ["managed"],
    test_class = "com.android.tools.adtui.AdtUiTestSuite",
    test_resources = ["src/test/resources"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/annotations[module]",
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/base/common:studio.common[module]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/idea/.idea/libraries:mockito",
        "//tools/adt/idea/adt-ui-model[module]",
        "//tools/idea/.idea/libraries:jcip",
        "//tools/adt/idea/observable[module]",
    ],
)

java_binary(
    name = "baseline-images-generator",
    main_class = "com.android.tools.adtui.imagediff.BaselineImagesGenerator",
    runtime_deps = [":adt-ui_testlib.javas"],
)
