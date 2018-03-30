load("//tools/base/bazel:bazel.bzl", "iml_module")

COMMON_DATA = [
    "//tools/adt/idea/android:test_deps.zip",
    "//prebuilts/studio/jdk",
    "//prebuilts/studio/sdk:build-tools/latest",
    "//prebuilts/studio/sdk:constraint-layout_latest",
    "//prebuilts/studio/sdk:platforms/latest",
    "//prebuilts/studio/sdk:platform-tools",
    "//prebuilts/tools/common/kotlin-plugin:Kotlin/kotlinc/build.txt",
    "//prebuilts/tools/common/kotlin-plugin:kotlin-plugin-for-studio",
    "//prebuilts/tools:bazel-dist",
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
    "cpu:3",
]

iml_module(
    name = "android-uitests",
    iml_files = ["android-uitests.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/platform/credential-store",
        "//tools/idea/java/compiler/instrumentation-util-8",
        "//tools/vendor/intellij/cidr:doxygen",
        "//tools/idea/plugins/settings-repository",
        "//tools/idea/jps/javac-ref-scanner-8",
    ],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/.idea/libraries:layoutlib[test]",
        "//tools/adt/idea/android[module, test]",
        "//tools/adt/idea/wizard-model[module, test]",
        "//tools/adt/idea/android-test-framework[module, test]",
        "//tools/idea:community-main[module, test]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/swing-testing/fest-swing[module, test]",
        "//tools/idea/java/debugger/openapi:debugger-openapi[module, test]",
        "//tools/idea/platform/analysis-api[module, test]",
        "//tools/idea/platform/platform-api[module, test]",
        "//tools/idea/platform/external-system-api[module, test]",
        "//tools/idea/java/openapi[module, test]",
        "//tools/idea/plugins/gradle[module, test]",
        "//tools/idea/java/compiler/openapi:compiler-openapi[module, test]",
        "//tools/idea:java-impl[module, test]",
        "//tools/idea/xml/dom-openapi[module, test]",
        "//tools/idea/platform/xdebugger-impl[module, test]",
        "//tools/adt/idea/designer[module, test]",
        "//tools/base/flags:studio.flags[module, test]",
        "//tools/analytics-library/shared:analytics-shared[module]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/adt/idea/adt-ui[module, test]",
        "//tools/adt/idea/artwork[module, test]",
        "//tools/adt/idea/android-common[module, test]",
        "//tools/idea/platform/xdebugger-api[module, test]",
        "//tools/adt/idea/adt-testutils[module, test]",
        "//tools/adt/idea/uitest-framework[module, test]",
        "//tools/adt/idea/uitest-framework-gradle[module, test]",
        "//tools/adt/idea/uitest-framework-bazel[module, test]",
        "//tools/idea/platform/testFramework[module, test]",
    ],
)

java_test(
    name = "GuiTestRuleTest",
    size = "large",
    data = COMMON_DATA + glob(["testData/SimpleLocalApplication/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.framework.GuiTestRuleTest"],
    tags = COMMON_TAGS,
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":android-uitests_testlib"],
)

java_test(
    name = "DataBindingTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:data/res",
        "//tools/data-binding:data_binding_runtime.zip",
    ] + glob([
        "testData/DatabindingMethodsTest/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.editing.DataBindingTest"],
    tags = COMMON_TAGS,
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":android-uitests_testlib"],
)

java_test(
    name = "ApkViewerTest",
    size = "large",
    data = COMMON_DATA + glob(["testData/SimpleLocalApplication/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.editors.ApkViewerTest"],
    tags = COMMON_TAGS + ["no_psq"],  # b/74906386
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":android-uitests_testlib"],
)

java_test(
    name = "NewProjectTest",
    size = "large",
    data = COMMON_DATA + ["//prebuilts/studio/layoutlib:data/res"],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.NewProjectTest"],
    tags = COMMON_TAGS + ["manual"],  #sanity test passing, but latestSupportLibraryWhileDndLayouts failed for UI reason
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":android-uitests_testlib"],
)

java_test(
    name = "NewActivityTest",
    size = "large",
    data = COMMON_DATA + ["//prebuilts/studio/layoutlib:data/res"] + glob([
        "testData/SimpleLocalApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.npw.NewActivityTest"],
    tags = COMMON_TAGS + ["manual"],  # b/74910563
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":android-uitests_testlib"],
)

java_test(
    name = "AddModuleTest",
    size = "large",
    data = COMMON_DATA + ["//prebuilts/studio/layoutlib:data/res"] + glob([
        "testData/SimpleLocalApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.instantapp.AddModuleTest"],
    tags = COMMON_TAGS + ["manual"],  #sanity test passing, some other test method failed for UI
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":android-uitests_testlib"],
)

java_test(
    name = "ConstraintLayoutTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:data/res",
    ] + glob([
        "testData/SimpleLocalApplication/**",
        "testData/LayoutLocalTest/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.ConstraintLayoutTest"],
    tags = COMMON_TAGS + ["manual"],  #Test failed for UI reason
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":android-uitests_testlib"],
)

java_test(
    name = "GradleSyncTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:data/res",
    ] + glob([
        "testData/SimpleLocalApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.gradle.GradleSyncTest"],
    tags = COMMON_TAGS + ["manual"],  #Sanity tests passing, but other tests fail
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":android-uitests_testlib"],
)

java_test(
    name = "NewModuleTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:data/res",
    ] + glob([
        "testData/SimpleLocalApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.gradle.NewModuleTest"],
    tags = COMMON_TAGS + ["no_psq"],  #sanity test passing, some other test method failed for UI
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":android-uitests_testlib"],
)

java_test(
    name = "BuildTypesTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:data/res",
    ] + glob([
        "testData/SimpleLocalApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.projectstructure.BuildTypesTest"],
    tags = COMMON_TAGS + ["manual"],  #UI flaky issue, fail sometimes
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":android-uitests_testlib"],
)

java_test(
    name = "LayoutInspectorTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:data/res",
    ] + glob([
        "testData/SimpleLocalApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.layoutinspector.LayoutInspectorTest"],
    tags = COMMON_TAGS + ["manual"],  #Test failed for UI reason
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":android-uitests_testlib"],
)

java_test(
    name = "ConvertToWebpActionTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:data/res",
    ] + glob([
        "testData/ImportLocalWebpProject/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.webp.ConvertToWebpActionTest"],
    tags = COMMON_TAGS + ["manual"],  #Sanity tests passing, but other tests fail
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":android-uitests_testlib"],
)

java_test(
    name = "FilterIconTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:data/res",
    ] + glob([
        "testData/SimpleLocalApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.webp.FilterIconTest"],
    tags = COMMON_TAGS + ["no_psq"],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":android-uitests_testlib"],
)

java_test(
    name = "LintTest",
    data = COMMON_DATA + glob([
        "testData/LintTest/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.lint.LintTest"],
    tags = COMMON_TAGS + ["no_psq"],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":android-uitests_testlib"],
)

java_test(
    name = "CreateNewFlavorsTest",
    data = COMMON_DATA + glob([
        "testData/SimpleLocalApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.projectstructure.CreateNewFlavorsTest"],
    tags = COMMON_TAGS + ["manual"],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":android-uitests_testlib"],
)
