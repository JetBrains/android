load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "project-system-gradle",
    # do not sort: must match IML order
    srcs = ["src"],
    iml_files = ["project-system-gradle.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/json",
        "//tools/idea/xml/relaxng",
        "//tools/idea/java/compiler/instrumentation-util-8",
        "//tools/idea/plugins/maven:maven2-server-impl",
        "//tools/adt/idea/adt-branding",
    ],
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
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/json",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/java/compiler/instrumentation-util-8",
        "//tools/idea/plugins/maven:maven2-server-impl",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/android[module, test]",
        "//tools/adt/idea/project-system[module, test]",
        "//tools/adt/idea/project-system-gradle[module, test]",
        "//tools/adt/idea/android-test-framework[module, test]",
        "//tools/idea:jps-builders_and_others[module, test]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/adt-testutils[module, test]",
    ],
)
