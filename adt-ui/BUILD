load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "adt-ui",
    srcs = ["src/main/java"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    test_resources = ["src/test/resources"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
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
)

java_binary(
    name = "baseline-images-generator",
    main_class = "com.android.tools.adtui.imagediff.BaselineImagesGenerator",
    runtime_deps = [":adt-ui_testlib.javas"],
)
