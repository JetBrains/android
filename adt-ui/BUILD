# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "adt-ui",
    srcs = ["src/main/java"],
    test_srcs = ["src/test/java"],
    test_resources = ["src/test/resources"],
    deps = [
        "//tools/base/annotations:studio.android-annotations[module]",
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools:idea.platform-api[module]",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/base/common:studio.common[module]",
        "//tools/idea/.idea/libraries:truth",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/idea/.idea/libraries:mockito",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
    tags = ["managed"],
)

java_binary(
    name = "baseline-images-generator",
    runtime_deps = [":adt-ui_testlib.javas"],
    main_class = "com.android.tools.adtui.imagediff.BaselineImagesGenerator",
)
