load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "project-system-gradle",
    # do not sort: must match IML order
    srcs = ["src"],
    iml_files = ["project-system-gradle.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/platform-api[module]",
        "//tools/adt/idea/project-system[module]",
        "//tools/adt/idea/android[module]",
        "//tools/adt/idea/android-common[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools/base/apkparser/analyzer:studio.analyzer[module]",
        "//tools/idea/platform/lang-api[module]",
        "//tools/analytics-library/protos:analytics-protos[module]",
    ],
)

iml_module(
    name = "project-system-gradle-tests",
    iml_files = ["project-system-gradle-tests.iml"],
    tags = ["managed"],
    test_class = "com.android.tools.idea.projectsystem.GradleProjectSystemTestSuite",
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/android[module, test]",
        "//tools/adt/idea/project-system[module, test]",
        "//tools/adt/idea/project-system-gradle[module, test]",
        "//tools/adt/idea/android-test-framework[module, test]",
        "//tools/idea:compiler-impl_and_others[module]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/adt-testutils[module, test]",
    ],
)
