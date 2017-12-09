load("//tools/base/bazel:bazel.bzl", "iml_module")

COMMON_DATA = [
    "//tools/adt/idea/android:test_deps.zip",
    "//prebuilts/studio/jdk",
    "//prebuilts/studio/sdk:build-tools/latest",
    "//prebuilts/studio/sdk:constraint-layout_latest",
    "//prebuilts/studio/sdk:platforms/latest",
    "//prebuilts/studio/sdk:platform-tools",
    "//tools/adt/idea/android/annotations",
    "//tools/adt/idea/artwork:device-art-resources",
    "//tools/adt/idea/adt-ui/lib/libwebp",
    "//tools/base/build-system:studio_repo.zip",
    "//tools/base/build-system:gradle-distrib",
    "//tools/base/templates",
    "//tools/idea/java/jdkAnnotations",
    "//tools/idea:config",
    "//tools/vendor/google/testing/display:xvfb",
]

COMMON_TAGS = [
    "block-network",
    "no_mac",
    "no_windows",
]

iml_module(
    name = "android-uitests",
    iml_files = ["android-uitests.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:credential-store",
        "//tools/idea/java/compiler/instrumentation-util-8",
        "//tools/idea/plugins/maven:maven2-server-impl",
        "//tools/vendor/intellij/cidr:doxygen",
        "//tools/idea/jps/javac-ref-scanner-8",
    ],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/uitest-framework[module, test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/.idea/libraries:layoutlib[test]",
        "//tools/adt/idea/android[module, test]",
        "//tools/adt/idea/wizard[module, test]",
        "//tools/adt/idea/android-test-framework[module, test]",
        "//tools/idea:community-main[module, test]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools:fest-swing[module, test]",
        "//tools/idea/java/debugger/openapi:debugger-openapi[module, test]",
        "//tools/idea/platform/analysis-api[module, test]",
        "//tools/idea/platform/platform-api[module, test]",
        "//tools/idea/platform/external-system-api[module, test]",
        "//tools/idea/java/openapi[module, test]",
        "//tools/idea/plugins/gradle[module, test]",
        "//tools/idea/java/compiler/openapi:compiler-openapi[module, test]",
        "//tools/idea:java-impl[module, test]",
        "//tools/idea:dom-openapi[module, test]",
        "//tools/idea:xdebugger-impl[module, test]",
        "//tools/adt/idea/designer[module, test]",
        "//tools/base/flags:studio.flags[module, test]",
        "//tools/analytics-library/shared:analytics-shared[module]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/adt/idea/adt-ui[module, test]",
        "//tools/adt/idea/artwork[module, test]",
        "//tools/adt/idea/android-common[module, test]",
        "//tools/idea/platform/xdebugger-api[module, test]",
        "//tools/adt/idea/adt-testutils[module, test]",
    ],
)

java_test(
    name = "GuiTestRuleTest",
    data = COMMON_DATA + glob(["testData/SimpleLocalApplication/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.framework.GuiTestRuleTest"],
    tags = COMMON_TAGS,
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":android-uitests_testlib"],
)

java_test(
    name = "DataBindingTest",
    data = COMMON_DATA + ["//tools/data-binding:data_binding_runtime.zip"] + glob([
        "testData/DatabindingMethodsTest/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.editing.DataBindingTest"],
    tags = COMMON_TAGS,
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":android-uitests_testlib"],
)

java_test(
    name = "ApkViewerTest",
    data = COMMON_DATA + glob(["testData/SimpleLocalApplication/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.editors.ApkViewerTest"],
    tags = COMMON_TAGS,
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":android-uitests_testlib"],
)
