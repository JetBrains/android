load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:utils.bzl", "select_project")
load("//tools/base/bazel:coverage.bzl", "coverage_java_test")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.updater.studio-updater",
    srcs = ["src"],
    iml_files = ["intellij.android.updater.studio-updater.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/dom-openapi:intellij.xml.dom",
        "//tools/idea/platform/inspect:intellij.platform.inspect",
        "//tools/idea/platform/testRunner:intellij.platform.testRunner",
        "//tools/idea/xml/xml-structure-view-impl:intellij.xml.structureView.impl",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
        "//tools/idea/spellchecker:intellij.spellchecker",
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/platform/testFramework/extensions:intellij.platform.testExtensions",
        "//tools/idea/platform/statistics/devkit:intellij.platform.statistics.devkit",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/images:intellij.platform.images",
        "//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl",
        "//tools/idea/platform/built-in-server:intellij.platform.builtInServer.impl",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/.idea/libraries:delight-rhino-sandbox",
        "//tools/idea/.idea/libraries:rhino",
        "//tools/idea/.idea/libraries:netty-handler-proxy",
        "//tools/idea/platform/diagnostic:intellij.platform.diagnostic",
        "//tools/idea/.idea/libraries:error-prone-annotations",
        "//tools/idea/.idea/libraries:javax.activation",
        "//tools/idea/.idea/libraries:jaxb-api",
        "//tools/idea/.idea/libraries:jaxb-runtime",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-jsr223",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-json",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-templates",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-xml",
    ],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],  # Tested via the integration_test target below
    visibility = ["//visibility:public"],
    runtime_deps = ["//prebuilts/tools/common/m2/repository/it/unimi/dsi/fastutil/8.4.0:jar"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:assertJ[test]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/analytics-library/shared:analytics-shared[module]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/idea/.idea/libraries:studio-analytics-proto",
        "//tools/idea/.idea/libraries:protobuf",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/idea/.idea/libraries:Guava[test]",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/updater:intellij.platform.updater[module]",
        "//tools/idea/platform/util:intellij.platform.util[module, test]",
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module, test]",
    ],
)

coverage_java_test(
    name = "integration_test",
    data = select_project({
        "": ["//tools/idea/updater:updater_deploy.jar"],
        "unb": ["//tools/adt/idea/studio:updater_deploy.jar"],
    }),
    jvm_flags = select_project({
        "": ["-Dupdater.jar.path=tools/idea/updater/updater_deploy.jar"],
        "unb": [],
    }),
    tags = ["no_test_windows"],  # b/77288863
    test_class = "com.android.studio.updater.StudioPatchUpdaterIntegrationTest",
    runtime_deps = [":intellij.android.updater.studio-updater_testlib"],
)

# managed by go/iml_to_build [unb]
iml_module(
    name = "intellij.android.updater.studio-updater",
    srcs = ["src"],
    iml_files = ["intellij.android.updater.studio-updater.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    project = "unb",
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-updater",
        "//tools/adt/idea/.idea/libraries:unb.assertJ[test]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/analytics-library/shared:analytics-shared[module]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/adt/idea/.idea/libraries:unb.studio-analytics-proto",
        "//tools/adt/idea/.idea/libraries:unb.protobuf",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/.idea/libraries:unb.Guava[test]",
        "//tools/adt/idea/.idea/libraries:unb.kotlin-stdlib-jdk8",
    ],
)
