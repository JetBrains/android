load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_repo")
load("//tools/base/bazel:maven.bzl", "maven_java_library")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.resources-base",
    srcs = ["src"],
    iml_files = ["intellij.android.resources-base.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
        "//tools/adt/idea/.idea/libraries:aapt-proto",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.resources-base.tests",
    iml_files = ["intellij.android.resources-base.tests.iml"],
    test_class = "com.android.tools.idea.resources.aar.ResourcesAarTestSuite",
    test_data = [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data/res",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/testData",
    ],
    test_srcs = ["testSrc"],
    test_tags = [
        "cpu:3",
        "block-network",
        "no_test_windows",
    ],
    test_timeout = "long",  # "medium" is not sufficient on Mac b/155999536
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module, test]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/android:intellij.android.core.tests[module, test]",
        "//tools/adt/idea/resources-base:intellij.android.resources-base[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/.idea/libraries:aapt-proto[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
    ],
)

java_binary(
    name = "framework-res-jar-creator",
    main_class = "com.android.tools.idea.resources.aar.FrameworkResJarCreator",
    runtime_deps = [":intellij.android.resources-base"],
)

genrule(
    name = "framework_res",
    srcs = [
        "//prebuilts/studio/layoutlib/data/res",
        "//prebuilts/studio/layoutlib/data/res:version",
    ],
    outs = ["framework_res.jar"],
    cmd = "$(JAVA) -classpath $(location :framework-res-jar-creator_deploy.jar) " + "com.android.tools.idea.resources.aar.FrameworkResJarCreator " + "$(location //prebuilts/studio/layoutlib/data/res:version)/.. $(OUTS)",
    toolchains = ["//prebuilts/studio/jdk:jdk_runtime"],
    tools = [
        ":framework-res-jar-creator",
        ":framework-res-jar-creator_deploy.jar",
    ],
    visibility = ["//visibility:public"],
)
