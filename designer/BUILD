load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "designer",
    srcs = ["src"],
    resources = ["resources"],
    tags = ["managed"],
    test_class = "com.android.tools.idea.DesignerTestSuite",
    test_data = [
        "//tools/adt/idea:testFiles",
        "//tools:idea/java/jdkAnnotations",
        "//tools/base/templates",
        "//prebuilts/studio/jdk",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:licenses",
        "//prebuilts/studio/sdk:platforms/latest",
        "//prebuilts/studio/layoutlib:data/res",
    ] + glob([
        "testData/**",
    ]),
    test_runtime_deps = [
        "//tools/adt/idea/adt-branding",
        "//tools/adt/idea/android-plugin:android-plugin",
    ],
    test_shard_count = 2,
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools:idea.platform-impl_and_others[module]",
        "//tools:idea.platform-api[module]",
        "//tools/adt/idea:android[module]",
        "//tools/sherpa:sherpa-solver[module]",
        "//tools/adt/idea:sherpa-ui[module]",
        "//tools/base/pixelprobe:studio.pixelprobe[module]",
        "//tools/base/annotations:studio.android-annotations[module]",
        "//tools/adt/idea:layoutlib[module]",
        "//tools:idea.java-psi-impl[module]",
        "//tools:idea.java-indexing-api[module]",
        "//tools/base/asset-studio:studio.assetstudio[module]",
        "//tools/adt/idea/adt-ui:adt-ui[module]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/idea/.idea/libraries:jsr305",
        "//tools:idea.compiler-impl_and_others[module]",
        "//tools/base/testutils:studio.testutils[module, test]",
    ],
)
