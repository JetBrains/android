load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "designer-tests",
    tags = ["managed"],
    test_class = "com.android.tools.idea.DesignerTestSuite",
    test_data = [
        "//tools/adt/idea:testFiles",
        "//tools/adt/idea/android/lib/androidWidgets",
        "//tools:idea/java/jdkAnnotations",
        "//tools/base/templates",
        "//prebuilts/studio/jdk",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:licenses",
        "//prebuilts/studio/sdk:platforms/latest",
        "//prebuilts/studio/layoutlib:buildprop",
        "//prebuilts/studio/layoutlib:data/fonts",
        "//prebuilts/studio/layoutlib:data/res",
    ] + glob([
        "testData/**",
    ]),
    test_resources = ["testData"],
    test_runtime_deps = [
        "//tools/adt/idea/adt-branding",
    ],
    test_srcs = ["testSrc"],
    test_timeout = "long",
    # do not sort: must match IML order
    deps = [
        "//tools:idea.platform-impl_and_others[module]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/designer[module, test]",
        "//tools/adt/idea:android[module, test]",
        "//tools:idea.xml-psi-api[module, test]",
        "//tools:idea.compiler-impl_and_others[module]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools:idea.lang-api[module, test]",
        "//tools/idea/.idea/libraries:layoutlib[test]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/adt/idea/adt-ui[module, test]",
        "//tools/base/pixelprobe:studio.pixelprobe[module, test]",
        "//tools/adt/idea:sherpa-ui[module, test]",
        "//tools/adt/idea/android-test-framework[module, test]",
    ],
)
