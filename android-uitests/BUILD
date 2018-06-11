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
    "//tools/idea:platform-build-info",
    "//tools/vendor/google/testing/display:xvfb",
]

COMMON_TAGS = [
    "block-network",
    "no_mac",
    "no_windows",
    "cpu:3",
    "slow",
]

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.guiTests",
    iml_files = ["intellij.android.guiTests.iml"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps",
        "//tools/vendor/intellij/cidr:intellij.c.doxygen",
        "//tools/idea/plugins/settings-repository:intellij.settingsRepository",
        "//tools/idea/jps/javac-ref-scanner-8:intellij.java.jps.javacRefScanner8",
    ],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/.idea/libraries:KotlinTest[test]",
        "//tools/idea/.idea/libraries:layoutlib[test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/wizard-model:intellij.android.wizard.model[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/idea:intellij.idea.community.main[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/swing-testing/fest-swing[module, test]",
        "//tools/idea/java/debugger/openapi:intellij.java.debugger[module, test]",
        "//tools/idea/platform/analysis-api:intellij.platform.analysis[module, test]",
        "//tools/idea/platform/platform-api:intellij.platform.ide[module, test]",
        "//tools/idea/platform/external-system-api:intellij.platform.externalSystem[module, test]",
        "//tools/idea/java/openapi:intellij.java[module, test]",
        "//tools/idea/plugins/gradle:intellij.gradle[module, test]",
        "//tools/idea/java/compiler/openapi:intellij.java.compiler[module, test]",
        "//tools/idea:intellij.java.impl[module, test]",
        "//tools/idea/xml/dom-openapi:intellij.xml.dom[module, test]",
        "//tools/idea/platform/xdebugger-impl:intellij.platform.debugger.impl[module, test]",
        "//tools/adt/idea/designer:intellij.android.designer[module, test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/analytics-library/shared:analytics-shared[module]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module, test]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/idea/platform/xdebugger-api:intellij.platform.debugger[module, test]",
        "//tools/adt/idea/adt-testutils[module, test]",
        "//tools/adt/idea/uitest-framework:intellij.android.guiTestFramework[module, test]",
        "//tools/adt/idea/uitest-framework-gradle[module, test]",
        "//tools/adt/idea/uitest-framework-bazel[module, test]",
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module, test]",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver[module, test]",
        "//tools/adt/idea/android-adb[module, test]",
    ],
)

java_test(
    name = "GuiTestRuleTest",
    size = "large",
    data = COMMON_DATA + glob(["testData/SimpleLocalApplication/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.framework.GuiTestRuleTest"],
    tags = COMMON_TAGS + ["manual"],  # b/110178024
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
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
    tags = COMMON_TAGS + ["manual"],  # b/110178024
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "ApkViewerTest",
    size = "large",
    data = COMMON_DATA + glob(["testData/SimpleLocalApplication/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.editors.ApkViewerTest"],
    tags = COMMON_TAGS + ["manual"],  # b/74906386
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "NewProjectTest",
    size = "large",
    data = COMMON_DATA + ["//prebuilts/studio/layoutlib:data/res"],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.NewProjectTest"],
    tags = COMMON_TAGS + ["manual"],  #sanity test passing, but latestSupportLibraryWhileDndLayouts failed for UI reason
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
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
    runtime_deps = [":intellij.android.guiTests_testlib"],
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
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "NlEditorTest",
    size = "large",
    data = COMMON_DATA + [
        # TODO: Remove the dependency on support-annotations once Bazel no longer requires support-annotations to build Android apps.
        "//prebuilts/studio/sdk:tools/support-annotations",
        "//prebuilts/studio/layoutlib:data/res",
        "//prebuilts/tools/common/m2/repository/com/android/support/appcompat-v7/26.1.0:aar",
        ":aswb_plugin",
    ] + glob([
        "testData/SimpleLocalApplication/**",
    ]) + glob(["testData/LayoutLocalTest/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.NlEditorTest"],
    tags = COMMON_TAGS + ["manual"],  #BasicLayoutTest failed for UI reason
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "BazelStartupSyncTest",
    data = COMMON_DATA + [
        # TODO: Remove the dependency on support-annotations once Bazel no longer requires support-annotations to build Android apps.
        "//prebuilts/studio/sdk:tools/support-annotations",
        "//prebuilts/studio/layoutlib:data/res",
        "//prebuilts/tools/common/m2/repository/com/android/support/appcompat-v7/26.1.0:aar",
        ":aswb_plugin",
    ] + glob([
        "testData/SimpleLocalApplication/**",
    ]) + glob(["testData/LayoutLocalTest/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.BazelStartupSyncTest"],
    tags = COMMON_TAGS + ["manual"],  # b/110178024
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "AswbGotoDeclarationTest",
    size = "large",
    data = COMMON_DATA + [
        # TODO: Remove the dependency on support-annotations once Bazel no longer requires support-annotations to build Android apps.
        "//prebuilts/studio/layoutlib:data/res",
        "//prebuilts/studio/sdk:tools/support-annotations",
        ":aswb_plugin",
    ] + glob([
        "testData/SimpleBazelApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.editing.AswbGotoDeclarationTest"],
    tags = COMMON_TAGS + [
        "manual",  # b/78573145
        "no_psq",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
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
    runtime_deps = [":intellij.android.guiTests_testlib"],
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
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "NewModuleTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:data/res",
    ] + glob([
        "testData/SimpleLocalApplication/**",
        "testData/LocalJarsAsModules/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.gradle.NewModuleTest"],
    tags = COMMON_TAGS + ["manual"],  # b/110178024
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
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
    runtime_deps = [":intellij.android.guiTests_testlib"],
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
    runtime_deps = [":intellij.android.guiTests_testlib"],
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
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "FilterIconTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:data/res",
    ] + glob([
        "testData/SimpleLocalApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.webp.FilterIconTest"],
    tags = COMMON_TAGS + ["manual"],  # b/110178024
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "LintTest",
    data = COMMON_DATA + glob([
        "testData/LintTest/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.lint.LintTest"],
    tags = COMMON_TAGS + ["manual"],  # b/110178024
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "CodeConversionFromJavaToKotlinTest",
    data = COMMON_DATA + glob([
        "testData/JavaToKotlinCode/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.kotlin.CodeConversionFromJavaToKotlinTest"],
    tags = COMMON_TAGS + ["manual"],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "CompileWithJava8Test",
    data = COMMON_DATA + glob([
        "testData/MinSdk24App/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.java8.CompileWithJava8Test"],
    tags = COMMON_TAGS + ["manual"],  # b/110178024
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "InferNullityTest",
    data = COMMON_DATA + glob([
        "testData/Nullity/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.editors.InferNullityTest"],
    tags = COMMON_TAGS + ["manual"],  # b/110178024
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "CreateDefaultActivityTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:data/res",
    ] + glob([
        "testData/SimpleLocalApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.npw.CreateDefaultActivityTest"],
    tags = COMMON_TAGS + ["manual"],  # b/110178024
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "GenerateApkWithReleaseVariantTest",
    data = COMMON_DATA + glob([
        "testData/SimpleLocalApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.instantapp.GenerateApkWithReleaseVariantTest"],
    tags = COMMON_TAGS + ["manual"],  # b/110178024
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "ModifyMinSdkAndSyncTest",
    data = COMMON_DATA + glob([
        "testData/SimpleLocalApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.gradle.ModifyMinSdkAndSyncTest"],
    tags = COMMON_TAGS + ["manual"],  # b/110178024
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "CreateNewAppModuleWithDefaultsTest",
    data = COMMON_DATA + glob([
        "testData/SimpleLocalApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.gradle.CreateNewAppModuleWithDefaultsTest"],
    tags = COMMON_TAGS + ["manual"],  # b/110178024
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "CreateNewLibraryModuleWithDefaultsTest",
    data = COMMON_DATA + glob([
        "testData/SimpleLocalApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.gradle.CreateNewLibraryModuleWithDefaultsTest"],
    tags = COMMON_TAGS + ["manual"],  # b/110178024
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "AddNewBuildTypeTest",
    data = COMMON_DATA + glob([
        "testData/SimpleLocalApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.projectstructure.AddNewBuildTypeTest"],
    tags = COMMON_TAGS + ["manual"],  # b/110178024
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "RunOnEmulatorTest",
    data = COMMON_DATA + glob([
        "testData/SimpleLocalApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.emulator.RunOnEmulatorTest"],
    tags = COMMON_TAGS + [
        "no_psq",
        "qa_sanity",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "AddKotlinTest",
    data = COMMON_DATA + glob([
        "testData/LinkProjectWithKotlin/**",
    ]),
    jvm_flags = [
        "-Dtest.suite.class=com.android.tools.idea.tests.gui.kotlin.AddKotlinTest",
    ],
    tags = COMMON_TAGS + [
        "no_psq",
        "qa_sanity",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [
        ":intellij.android.guiTests_testlib",
    ],
)

java_test(
    name = "AndroidProfilerTest",
    data = COMMON_DATA + glob(["testData/MinimalMinSdk26Apk/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.profiler.AndroidProfilerTest"],
    tags = COMMON_TAGS + ["manual"],  # b/110178024
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "UnusedResourceEvaluationTest",
    data = COMMON_DATA + glob([
        "testData/NoUnusedResourceApp/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.UnusedResourceEvaluationTest"],
    tags = COMMON_TAGS + [
        "no_psq",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "SideConstraintHandlingTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:buildprop",
        "//prebuilts/studio/layoutlib:data/fonts",
        "//prebuilts/studio/layoutlib:data/res",
        "//tools/adt/idea/android/lib:androidWidgets",
    ] + glob([
        "testData/LayoutLocalTest/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.SideConstraintHandlingTest"],
    tags = COMMON_TAGS + ["manual"],  # b/110178024
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "BaselineConstraintHandlingTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:buildprop",
        "//prebuilts/studio/layoutlib:data/fonts",
        "//prebuilts/studio/layoutlib:data/res",
        "//tools/adt/idea/android/lib:androidWidgets",
    ] + glob([
        "testData/LayoutLocalTest/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.BaselineConstraintHandlingTest"],
    tags = COMMON_TAGS + ["manual"],  # b/110178024
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "AddAppCompatLibTest",
    data = COMMON_DATA + glob([
        "testData/NoAppCompatLibApp/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.projectstructure.AddAppCompatLibTest"],
    tags = COMMON_TAGS + [
        "no_psq",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "CreateLoginActivityTest",
    data = COMMON_DATA,
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.npw.CreateLoginActivityTest"],
    tags = COMMON_TAGS + [
        "no_psq",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "CreateSettingsActivityTest",
    data = COMMON_DATA,
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.npw.CreateSettingsActivityTest"],
    tags = COMMON_TAGS + [
        "no_psq",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "AddLocaleTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:buildprop",
        "//prebuilts/studio/layoutlib:data/fonts",
        "//prebuilts/studio/layoutlib:data/res",
        "//tools/adt/idea/android/lib:androidWidgets",
    ] + glob([
        "testData/SimpleLocalApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.editors.translations.AddLocaleTest"],
    tags = COMMON_TAGS + [
        "no_psq",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "SurroundWithShortcutTest",
    data = COMMON_DATA + glob([
        "testData/SimpleLocalApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.editors.SurroundWithShortcutTest"],
    tags = COMMON_TAGS + [
        "no_psq",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "ForeachLiveTemplateTest",
    data = COMMON_DATA + glob([
        "testData/SimpleLocalApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.editors.ForeachLiveTemplateTest"],
    tags = COMMON_TAGS + [
        "no_psq",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "ForiLiveTemplateTest",
    data = COMMON_DATA + glob([
        "testData/SimpleLocalApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.editors.ForiLiveTemplateTest"],
    tags = COMMON_TAGS + [
        "no_psq",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "ToastLiveTemplateTest",
    data = COMMON_DATA + glob([
        "testData/SimpleLocalApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.editors.ToastLiveTemplateTest"],
    tags = COMMON_TAGS + [
        "no_psq",
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "BasicLayoutEditTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:buildprop",
        "//prebuilts/studio/layoutlib:data/fonts",
        "//prebuilts/studio/layoutlib:data/res",
        "//tools/adt/idea/android/lib:androidWidgets",
        ":aswb_plugin",
    ] + glob([
        "testData/SimpleLocalApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.BasicLayoutEditTest"],
    tags = COMMON_TAGS + ["manual"],  # b/110178024
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "FlavorsExecutionTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:buildprop",
        "//prebuilts/studio/layoutlib:data/fonts",
        "//prebuilts/studio/layoutlib:data/res",
        "//tools/adt/idea/android/lib:androidWidgets",
    ] + glob([
        "testData/SimpleFlavoredApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.projectstructure.FlavorsExecutionTest"],
    tags = COMMON_TAGS + [
        "no_psq",
        "qa_sanity",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "ConvertFromPngToWebpTest",
    size = "large",
    data = COMMON_DATA + glob([
        "testData/ImportLocalWebpProject/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.webp.ConvertFromPngToWebpTest"],
    tags = COMMON_TAGS + [
        "no_psq",
        "qa_sanity",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "ConvertFromWebpToPngTest",
    size = "large",
    data = COMMON_DATA + glob([
        "testData/ImportLocalWebpProject/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.webp.ConvertFromWebpToPngTest"],
    tags = COMMON_TAGS + [
        "no_psq",
        "qa_sanity",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "CreateNewFlavorsTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/sdk:platforms/android-24",
    ] + glob([
        "testData/SimpleLocalApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.projectstructure.CreateNewFlavorsTest"],
    tags = COMMON_TAGS + ["manual"],  # b/110178024
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "LaunchApkViewerTest",
    size = "large",
    data = COMMON_DATA + glob([
        "testData/SimpleLocalApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.editors.LaunchApkViewerTest"],
    tags = COMMON_TAGS + [
        "no_psq",
        "qa_sanity",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "CreateBasicKotlinProjectTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:data/res",
    ],
    jvm_flags = [
        "-Dtest.suite.class=com.android.tools.idea.tests.gui.kotlin.CreateBasicKotlinProjectTest",
    ],
    tags = COMMON_TAGS + [
        "no_psq",
        "qa_sanity",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

genrule(
    name = "aswb_plugin",
    srcs = ["@blaze//third_party/intellij/bazel/plugin/aswb:aswb_bazel_zip"],
    outs = [
        "aswb/aspect/WORKSPACE",
        "aswb/aspect/BUILD.bazel",
        "aswb/aspect/artifacts.bzl",
        "aswb/aspect/intellij_info_bundled.bzl",
        "aswb/aspect/intellij_info_impl.bzl",
        "aswb/aspect/java_classpath.bzl",
        "aswb/aspect/tools/JarFilter_deploy.jar",
        "aswb/aspect/tools/PackageParser_deploy.jar",
        "aswb/lib/aswb_bazel.jar",
    ],
    cmd = "unzip $(location @blaze//third_party/intellij/bazel/plugin/aswb:aswb_bazel_zip) -d $(@D)",
)
