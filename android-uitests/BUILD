load("//tools/base/bazel:bazel.bzl", "iml_module")

filegroup(
    name = "aspect_rules",
    srcs = ["default_aspect_rules.json"],
)

filegroup(
    name = "aspects_baseline",
    srcs = ["aspects_baseline.txt"],
)

COMMON_DATA = [
    "//tools/adt/idea/android-uitests:aspects_baseline",
    "//tools/adt/idea/android-uitests:aspect_rules",
    "//tools/base/aspects_agent:aspects_agent",
    "//tools/adt/idea/android:test_deps.zip",
    "//prebuilts/studio/jdk",
    "//prebuilts/studio/sdk:build-tools/latest",
    "//prebuilts/studio/sdk:cmake",
    "//prebuilts/studio/sdk:ndk",
    "//prebuilts/studio/sdk:ndk-bundle",
    "//prebuilts/studio/sdk:platforms/latest",
    "//prebuilts/studio/sdk:platform-tools",
    "//prebuilts/tools/common/aspects-agent:aspects_agent",
    "//prebuilts/tools/common/kotlin-plugin:Kotlin/kotlinc/build.txt",
    "//prebuilts/tools:bazel-dist",
    "//tools/adt/idea/android/annotations",
    "//tools/adt/idea/artwork:device-art-resources",
    "//tools/base/build-system:studio_repo.zip",
    "//tools/base/build-system:gradle-distrib",
    "//tools/base/third_party/kotlin:kotlin-m2repository.zip",
    "//tools/data-binding:data_binding_runtime.zip",
    "//tools/vendor/google/testing/display:xvfb",
]

COMMON_TAGS = [
    "block-network",
    "no_windows",
    "cpu:3",
]

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.guiTests",
    iml_files = ["intellij.android.guiTests.iml"],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/.idea/libraries:emulator-proto[test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/emulator:intellij.android.emulator[module, test]",
        "//tools/adt/idea/wizard-model:intellij.android.wizard.model[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/swing-testing/fest-swing[module, test]",
        "//tools/adt/idea/designer:intellij.android.designer[module, test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/analytics-library/shared:analytics-shared[module]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module, test]",
        "//tools/adt/idea/layout-ui:intellij.android.layout-ui[module, test]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/uitest-framework:intellij.android.guiTestFramework[module, test]",
        "//tools/adt/idea/bleak:intellij.android.bleak[module, test]",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver[module, test]",
        "//tools/adt/idea/android-adb:intellij.android.adb[module, test]",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing[module, test]",
        "//tools/base/perf-logger:studio.perf-logger[module, test]",
        "//tools/adt/idea/nav/editor:intellij.android.nav.editor[module, test]",
        "//tools/base/deploy/deployer:studio.android.sdktools.deployer[module, test]",
        "//tools/adt/idea/deploy:intellij.android.deploy[module, test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module, test]",
        "//tools/adt/idea/.idea/libraries:layoutlib[test]",
        "//tools/adt/idea/android-uitests:native_artifacts",
        "//tools/adt/idea/layout-inspector:intellij.android.layout-inspector[module, test]",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module, test]",
        "//tools/adt/idea/android-transport:intellij.android.transport[module, test]",
        "//tools/adt/idea/.idea/libraries:transport-proto[test]",
        "//tools/adt/idea/.idea/libraries:layoutinspector-skia-proto[test]",
        "//tools/adt/idea/.idea/libraries:studio-proto[test]",
        "//tools/adt/idea/.idea/libraries:studio-grpc[test]",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl[module]",
        "//tools/base/wizard/template-plugin:studio.intellij.android.wizardTemplate.plugin[module, test]",
        "//tools/adt/idea/project-system-gradle-psd:intellij.android.projectSystem.gradle.psd[module]",
        "//tools/base/wizard/template-impl:studio.intellij.android.wizardTemplate.impl[module, test]",
        "//tools/adt/idea/android-plugin:intellij.android.plugin[module, test]",
        "//tools/adt/idea/studio[module, test]",
        "//tools/adt/idea/app-inspection/api:intellij.android.app-inspection.api[module, test]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
    ],
)

filegroup(
    name = "native_artifacts",
    srcs = ["//tools/adt/idea/android-uitests/native/testing:liblayout_inspector_test_support.so"],
)

java_test(
    name = "LayoutEditorMemoryUseTest",
    size = "large",
    data = COMMON_DATA + glob(["testData/LayoutTest/**"]) + glob(["testData/SimpleApplication/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.performance.LayoutEditorMemoryUseTest"],
    tags = COMMON_TAGS + [
        "very_flaky",
        "no_mac",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "InspectionsMemoryUseTest",
    size = "large",
    data = COMMON_DATA + glob(["testData/SimpleApplication/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.performance.InspectionsMemoryUseTest"],
    tags = COMMON_TAGS + [
        "very_flaky",
        "no_mac",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "GradleSyncMemoryUseTest",
    size = "large",
    data = COMMON_DATA + glob(["testData/SimpleApplication/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.performance.GradleSyncMemoryUseTest"],
    tags = COMMON_TAGS + [
        "very_flaky",
        "no_mac",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "FindInPathMemoryLeakTest",
    size = "large",
    data = COMMON_DATA + glob(["testData/SimpleApplication/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.performance.FindInPathMemoryLeakTest"],
    tags = COMMON_TAGS + [
        "very_flaky",
        "no_mac",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "EditorTabsMemoryUseTest",
    size = "large",
    data = COMMON_DATA + glob(["testData/SimpleApplication/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.performance.EditorTabsMemoryUseTest"],
    tags = COMMON_TAGS + [
        "very_flaky",
        "no_mac",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "AddResourceMemoryUseTest",
    size = "large",
    data = COMMON_DATA + glob(["testData/SimpleApplication/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.performance.AddResourceMemoryUseTest"],
    tags = COMMON_TAGS + [
        "very_flaky",
        "no_mac",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "AddAssetMemoryUseTest",
    size = "large",
    data = COMMON_DATA + glob(["testData/SimpleApplication/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.performance.AddAssetMemoryUseTest"],
    tags = COMMON_TAGS + [
        "very_flaky",
        "no_mac",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "NavEditorMemoryUseTest",
    size = "large",
    data = COMMON_DATA + glob(["testData/Navigation/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.performance.NavEditorMemoryUseTest"],
    tags = COMMON_TAGS + [
        "very_flaky",
        "no_mac",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "VisualizationTest",
    size = "large",
    data = COMMON_DATA + glob(["testData/SimpleApplication/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.VisualizationTest"],
    tags = COMMON_TAGS + [
        "very_flaky",
        "no_mac",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "ComposePreviewTest",
    size = "large",
    data = COMMON_DATA + glob(["testData/SimpleComposeApplication/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.compose.ComposePreviewTest"],
    tags = COMMON_TAGS + [
        "very_flaky",
        "no_mac",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "CustomViewPreviewTest",
    size = "large",
    data = COMMON_DATA + glob(["testData/SimpleComposeApplication/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.customview.CustomViewPreviewTest"],
    tags = COMMON_TAGS + [
        "very_flaky",
        "no_mac",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "GuiTestRuleTest",
    size = "large",
    data = COMMON_DATA + glob(["testData/SimpleApplication/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.framework.GuiTestRuleTest"],
    tags = COMMON_TAGS + ["manual"],  # b/110178024
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "BasicLayoutInspectorUITest",
    size = "large",
    data = COMMON_DATA + ["//prebuilts/studio/layoutlib/data:framework_res.jar"] + glob(["testData/LayoutTest/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.layoutinspector.BasicLayoutInspectorUITest"],
    tags = COMMON_TAGS + ["manual"],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [
        ":intellij.android.guiTests_testlib",
        "//tools/adt/idea/android-uitests/native/testing:liblayout_inspector_test_support.so",
        "//tools/base/dynamic-layout-inspector/skia:skia-grpc-server",
    ],
)

java_test(
    name = "ResolveXmlReferencesTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ] + glob([
        "testData/DatabindingMethodsTest/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.editing.ResolveXmlReferencesTest"],
    tags = COMMON_TAGS + ["manual"],  # b/110178024
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "NewProjectTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.NewProjectTest"],
    tags = COMMON_TAGS + ["manual"],  #sanity test passing, but latestSupportLibraryWhileDndLayouts failed for UI reason
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "NewActivityTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ] + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.npw.NewActivityTest"],
    tags = COMMON_TAGS + ["manual"],  # b/74910563
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "NlEditorTest",
    size = "large",
    data = COMMON_DATA + [
        # TODO: Remove the dependency on support-annotations once Bazel no longer requires support-annotations to build Android apps.
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/tools/common/m2/repository/com/android/support/support-annotations/28.0.0:jar",
        "//prebuilts/tools/common/m2/repository/com/android/support/appcompat-v7/26.1.0:aar",
    ] + glob([
        "testData/SimpleApplication/**",
    ]) + glob(["testData/LayoutTest/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.NlEditorTest"],
    tags = COMMON_TAGS + ["manual"],  #BasicLayoutTest failed for UI reason
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "ConstraintLayoutTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ] + glob([
        "testData/SimpleApplication/**",
        "testData/LayoutTest/**",
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
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ] + glob([
        "testData/SimpleApplication/**",
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
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ] + glob([
        "testData/SimpleApplication/**",
        "testData/LocalJarsAsModules/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.gradle.NewModuleTest"],
    tags = COMMON_TAGS + ["manual"],  # b/110178024
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "BuildTypesTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ] + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.projectstructure.BuildTypesTest"],
    tags = COMMON_TAGS + ["manual"],  #UI flaky issue, fail sometimes
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "LayoutInspectorTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ] + glob([
        "testData/SimpleApplication/**",
        "testData/LayoutInspector/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.layoutinspector.LayoutInspectorTest"],
    tags = COMMON_TAGS + [
        "qa_sanity",
        "qa_unreliable",  # b/139761045
        "qa_sanity_unreliable",  # b/139761045
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "ConvertToWebpActionTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
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
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ] + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.webp.FilterIconTest"],
    tags = COMMON_TAGS + [
        "qa_fast",
    ],
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

java_test(
    name = "AndroidProfilerTest",
    data = COMMON_DATA + glob(["testData/MinimalMinSdk26Apk/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.profiler.AndroidProfilerTest"],
    tags = COMMON_TAGS + ["manual"],  # b/154963251
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "DeploymentTest",
    data = COMMON_DATA + [
        "//tools/base/deploy/deployer:test_apks",
    ] + glob([
        "testData/simple/**",
    ]) + select({
        "//tools/base/bazel:windows": [],
        "//conditions:default": [
            "//tools/base/deploy/installer:test-installer",
            "//tools/base/deploy/installer/tests:artifacts",
        ],
    }),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.deploy.DeploymentTest"],
    tags = COMMON_TAGS + [
        "no_mac",
        "ui_test",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "TerminateAdbIfNotUsedTest",
    data = COMMON_DATA + glob(["testData/simple/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.adb.TerminateAdbIfNotUsedTest"],
    tags = COMMON_TAGS + ["manual"],  # b/154963251
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "EmbeddedEmulatorTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/android-emulator:emulator",
        "@system_image_latest_default_x86_64//:images",
    ] + glob(["testData/TextInput/**"]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.emulator.end2end.EmbeddedEmulatorTest"],
    tags = COMMON_TAGS + ["manual"],  # b/154963251
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
    tags = COMMON_TAGS + [
        "qa_sanity",  # fails 0 out of 3000 runs
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "CreateDefaultActivityTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ] + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.npw.CreateDefaultActivityTest"],
    tags = COMMON_TAGS + [
        "qa_sanity",  # fails 0 out of 3000 runs
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "GenerateApkWithReleaseVariantTest",
    data = COMMON_DATA + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.instantapp.GenerateApkWithReleaseVariantTest"],
    tags = COMMON_TAGS + [
        "qa_sanity",  # b/110178024
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "ModifyMinSdkAndSyncTest",
    data = COMMON_DATA + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.gradle.ModifyMinSdkAndSyncTest"],
    tags = COMMON_TAGS + [
        "qa_sanity",  # fails 1 out of 3000 runs
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "CreateNewAppModuleWithDefaultsTest",
    data = COMMON_DATA + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.gradle.CreateNewAppModuleWithDefaultsTest"],
    tags = COMMON_TAGS + [
        "qa_sanity",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "CreateNewLibraryModuleWithDefaultsTest",
    data = COMMON_DATA + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.gradle.CreateNewLibraryModuleWithDefaultsTest"],
    tags = COMMON_TAGS + ["manual"],  # b/154963251
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "AddNewBuildTypeTest",
    data = COMMON_DATA + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.projectstructure.AddNewBuildTypeTest"],
    tags = COMMON_TAGS + [
        "qa_sanity",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "RunOnEmulatorTest",
    data = COMMON_DATA + glob([
        "testData/SimpleApplication/**",
    ]),
    flaky = True,
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.emulator.RunOnEmulatorTest"],
    tags = COMMON_TAGS + [
        "ui_test",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "AddKotlinTest",
    size = "large",
    data = COMMON_DATA + glob([
        "testData/LinkProjectWithKotlin/**",
    ]),
    jvm_flags = [
        "-Dtest.suite.class=com.android.tools.idea.tests.gui.kotlin.AddKotlinTest",
    ],
    tags = COMMON_TAGS + [
        "qa_sanity",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [
        ":intellij.android.guiTests_testlib",
    ],
)

# sanity test
java_test(
    name = "SideConstraintHandlingTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data:native_libs",
    ] + glob([
        "testData/LayoutTest/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.SideConstraintHandlingTest"],
    tags = COMMON_TAGS + [
        "qa_sanity",  # b/110178024
        "qa_unreliable",  # bug: b/78469702
        "qa_sanity_unreliable",  # bug: b/78469702
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "BaselineConstraintHandlingTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data:native_libs",
    ] + glob([
        "testData/LayoutTest/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.BaselineConstraintHandlingTest"],
    tags = COMMON_TAGS + [
        "qa_sanity",
        "qa_sanity_unreliable",  # b/159635974
        "qa_unreliable",  # b/159635974
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "BasicLayoutEditTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data:native_libs",
    ] + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.BasicLayoutEditTest"],
    tags = COMMON_TAGS + [
        "qa_sanity",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "OpenCloseVisualizationToolTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data:native_libs",
    ] + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.OpenCloseVisualizationToolTest"],
    tags = COMMON_TAGS + [
        "qa_sanity",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "FlavorsExecutionTest",
    size = "enormous",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data:native_libs",
    ] + glob([
        "testData/SimpleFlavoredApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.projectstructure.FlavorsExecutionTest"],
    tags = COMMON_TAGS + [
        "qa_sanity",
        "qa_sanity_unreliable",  # b/160247726
        "qa_unreliable",  # b/160247726
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
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.projectstructure.CreateNewFlavorsTest"],
    tags = COMMON_TAGS + [
        "qa_sanity",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "LaunchApkViewerTest",
    size = "large",
    data = COMMON_DATA + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.editors.LaunchApkViewerTest"],
    tags = COMMON_TAGS + [
        "qa_sanity",
        "no_crostini",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "CreateBasicKotlinProjectTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ],
    jvm_flags = [
        "-Dtest.suite.class=com.android.tools.idea.tests.gui.kotlin.CreateBasicKotlinProjectTest",
    ],
    tags = COMMON_TAGS + [
        "qa_sanity",  # fails 1 out of 3000 runs
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "AddRemoveCppDependencyTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ],
    jvm_flags = [
        "-Dtest.suite.class=com.android.tools.idea.tests.gui.npw.AddRemoveCppDependencyTest",
    ],
    tags = COMMON_TAGS + [
        "qa_sanity",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "CreateNewProjectWithCpp1Test",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.npw.CreateNewProjectWithCpp1Test"],
    tags = COMMON_TAGS + [
        "qa_sanity",  # fails 1 out of 3000 runs
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "CreateNewProjectWithCpp2Test",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.npw.CreateNewProjectWithCpp2Test"],
    tags = COMMON_TAGS + [
        "qa_sanity",  # fails 1 out of 3000 runs
        "no_crostini",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "CreateNewProjectWithCpp3Test",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.npw.CreateNewProjectWithCpp3Test"],
    tags = COMMON_TAGS + [
        "qa_sanity",  # fails 0 out of 3000 runs
        "no_crostini",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "CreateNewMobileProjectTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.CreateNewMobileProjectTest"],
    tags = COMMON_TAGS + [
        "qa_sanity",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "CreateCppKotlinProjectTest",
    size = "large",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ] + glob([
        "testData/CppKotlin/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.kotlin.CreateCppKotlinProjectTest"],
    tags = COMMON_TAGS + [
        "qa_sanity",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "BuildCppKotlinTest",
    data = COMMON_DATA + glob([
        "testData/CppKotlin/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.kotlin.BuildCppKotlinTest"],
    tags = COMMON_TAGS + [
        "qa_sanity",
        "no_crostini",
        "qa_sanity_unreliable",  # b/161711253
        "qa_unreliable",  # b/161711253
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "AbiSplitApksTest",
    size = "enormous",
    data = COMMON_DATA + glob([
        "testData/debugger/BasicCmakeAppForUI/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.debugger.AbiSplitApksTest"],
    tags = COMMON_TAGS + [
        "qa_sanity",
        "no_crostini",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "X86AbiSplitApksTest",
    size = "enormous",
    data = COMMON_DATA + glob([
        "testData/debugger/BasicCmakeAppForUI/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.debugger.X86AbiSplitApksTest"],
    tags = COMMON_TAGS + [
        "qa_sanity",
        "no_crostini",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "LocalApkProjTest",
    size = "large",
    data = COMMON_DATA + glob([
        "testData/ApkDebug/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.debugger.LocalApkProjTest"],
    tags = COMMON_TAGS + [
        "no-remote",
        "requires-fakeroot",
        "qa_sanity",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "ImportSampleProjectTest",
    data = COMMON_DATA + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.emulator.ImportSampleProjectTest"],
    tags = [
        "cpu:3",
        "no-remote",
        "no_mac",
        "no_windows",
        "qa_sanity",
        "qa_sanity_unreliable",  # b/76023451
        "qa_unreliable",  # b/76023451
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "InstallPackageTest",
    data = COMMON_DATA + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.sdkmanager.InstallPackageTest"],
    tags = [
        "cpu:100",
        "no-remote",
        "no_mac",
        "no_windows",
        "qa_sanity",
        "qa_sanity_emu",  # Run together with emulators because the test needs to run alone
        "qa_sanity_unreliable_emu",  # b/119505019
        "qa_unreliable",  # b/119505019
        "requires-network",
        "requires_emulator",  # Run together with emulators because the test needs to run alone
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

config_setting(
    name = "has_emulator",
    define_values = {
        "external_emulator": "true",
    },
)

# sanity test
java_test(
    name = "DebugOnEmulatorTest",
    size = "enormous",
    data = COMMON_DATA + glob([
        "testData/SimpleApplication/**",
    ]) + [
        "//prebuilts/studio/sdk:platforms/android-23",
    ] + select({
        ":has_emulator": [
            "@externsdk//:system-images",
            "@externsdk//:emulator",
        ],
        "//conditions:default": [],
    }),
    jvm_flags = [
        "-Dtest.suite.class=com.android.tools.idea.tests.gui.emulator.DebugOnEmulatorTest",
    ],
    # do not sort: the beginning of this list is copied from COMMON_TAGS
    tags = [
        "block-network",
        "no_mac",
        "no_windows",
        "no-remote",
        "requires-fakeroot",
        "qa_sanity",
        "qa_sanity_emu",
        "requires_emulator",
        "cpu:100",
        "qa_sanity_unreliable_emu",  # intend to run it here to see stable sanity ui tests running time
        "qa_unreliable",  # intend to run it here to see stable sanity ui tests running time
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "CreateNavGraphTest",
    data = COMMON_DATA + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.naveditor.CreateNavGraphTest"],
    tags = COMMON_TAGS + [
        "qa_sanity",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "CreateAndRunInstantAppTest",
    size = "enormous",
    data = COMMON_DATA + select({
        ":has_emulator": [
            "@externsdk//:system-images",
            "@externsdk//:emulator",
        ],
        "//conditions:default": [],
    }) + [
        "//prebuilts/studio/sdk:instant-apps-sdk",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.instantapp.CreateAndRunInstantAppTest"],
    tags = [
        "block-network",
        "cpu:100",
        "no-remote",
        "no_mac",
        "no_windows",
        "qa_sanity",
        "qa_sanity_emu",
        "qa_sanity_unreliable_emu",  # b/147893119
        "qa_unreliable",  # b/147893119
        "requires-fakeroot",
        "requires_emulator",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "DualDebuggerBreakpointsTest",
    size = "enormous",
    data = COMMON_DATA + glob([
        "testData/debugger/BasicCmakeAppForUI/**",
    ]) + select({
        ":has_emulator": [
            "@externsdk//:system-images",
            "@externsdk//:emulator",
        ],
        "//conditions:default": [],
    }) + [
        "//prebuilts/tools:lldb",
        "//prebuilts/python/linux-x86:linux-x86",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.debugger.DualDebuggerBreakpointsTest"],
    tags = [
        "block-network",
        "cpu:100",
        "no-remote",
        "no_mac",
        "no_windows",
        "qa_sanity",  # b/128932526
        "qa_sanity_emu",  # b/128932526
        "qa_sanity_unreliable_emu",  # b/128932526
        "qa_unreliable",  # b/128932526
        "requires-fakeroot",
        "requires_emulator",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "RunInstrumentationTest",
    size = "enormous",
    data = COMMON_DATA + glob([
        "testData/InstrumentationTest/**",
    ]) + select({
        ":has_emulator": [
            "@externsdk//:system-images",
            "@externsdk//:emulator",
        ],
        "//conditions:default": [],
    }),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.emulator.RunInstrumentationTest"],
    tags = [
        "block-network",
        "cpu:100",
        "no-remote",
        "no_mac",
        "no_windows",
        "qa_sanity",
        "qa_sanity_emu",
        "qa_sanity_unreliable",  # b/145991485
        "qa_unreliable",  # b/145991485
        "requires-fakeroot",
        "requires_emulator",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "EspressoRecorderTest",
    size = "enormous",
    data = COMMON_DATA + glob([
        "testData/SimpleApplication/**",
    ]) + select({
        ":has_emulator": [
            "@externsdk//:system-images",
            "@externsdk//:emulator",
        ],
        "//conditions:default": [],
    }),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.espresso.EspressoRecorderTest"],
    tags = [
        "block-network",
        "cpu:100",
        "no-remote",
        "no_mac",
        "no_windows",
        "qa_sanity",
        "qa_sanity_emu",
        "qa_sanity_unreliable_emu",  # b/77635374
        "qa_unreliable",  # b/77635374
        "requires-fakeroot",
        "requires_emulator",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "CMakeListsTest",
    size = "large",
    data = COMMON_DATA + glob([
        "testData/debugger/CMakeListsHelloJni/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.cpp.CMakeListsTest"],
    tags = COMMON_TAGS + [
        "qa_sanity",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "NewComposeProjectTest",
    size = "large",
    data = COMMON_DATA,
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.compose.NewComposeProjectTest"],
    tags = COMMON_TAGS + [
        "qa_sanity",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "QuickFixForJniTest",
    size = "large",
    data = COMMON_DATA + glob([
        "testData/debugger/CLionNdkHelloJni/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.cpp.QuickFixForJniTest"],
    tags = COMMON_TAGS + [
        "qa_sanity",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "OpenExistingProjectTest",
    size = "large",
    data = COMMON_DATA,
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.OpenExistingProjectTest"],
    tags = COMMON_TAGS + [
        "qa_sanity",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "CodeGenerationTest",
    size = "large",
    data = COMMON_DATA + glob([
        "testData/CodeGeneration/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.intellijplatform.CodeGenerationTest"],
    tags = COMMON_TAGS + [
        "qa_sanity",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "GotoDeclarationTest",
    size = "large",
    data = COMMON_DATA + glob([
        "testData/CodeGeneration/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.intellijplatform.GotoDeclarationTest"],
    tags = COMMON_TAGS + [
        "qa_sanity",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# sanity test
java_test(
    name = "AddInstantModuleTest",
    size = "large",
    data = COMMON_DATA + glob([
        "testData/MinSdk24App/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.instantapp.AddInstantModuleTest"],
    tags = COMMON_TAGS + [
        "qa_sanity",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "LintTest",
    data = COMMON_DATA + glob([
        "testData/LintTest/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.lint.LintTest"],
    tags = COMMON_TAGS + [
        "qa_fast",  # b/110178024
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "UnusedResourceEvaluationTest",
    data = COMMON_DATA + glob([
        "testData/NoUnusedResourceApp/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.UnusedResourceEvaluationTest"],
    tags = COMMON_TAGS + [
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "JavaLibsDepTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.projectstructure.JavaLibsDepTest"],
    tags = COMMON_TAGS + [
        "qa_fast",
        "qa_unreliable",  # b/117238132
        "qa_fast_unreliable",  # b/117238132
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "AndroidLibsDepTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.projectstructure.AndroidLibsDepTest"],
    tags = COMMON_TAGS + [
        "qa_fast",
        "qa_unreliable",  # b/117238132
        "qa_fast_unreliable",  # b/117238132
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "JavaDepTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/tools/common/m2/repository/com/google/code/gson/gson/2.6.2:jar",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.projectstructure.JavaDepTest"],
    tags = COMMON_TAGS + [
        "qa_fast",
        "qa_unreliable",  # b/117238132
        "qa_fast_unreliable",  # b/117238132
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "AndroidDepTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/tools/common/m2/repository/com/google/code/gson/gson/2.6.2:jar",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.projectstructure.AndroidDepTest"],
    tags = COMMON_TAGS + [
        "qa_fast",
        "qa_unreliable",  # b/111889561
        "qa_fast_unreliable",  # b/111889561
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "IdePermissionTest",
    data = COMMON_DATA + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.editing.IdePermissionTest"],
    tags = COMMON_TAGS + [
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "PrivateResourceTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ] + glob([
        "testData/PrivateResource/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.editing.PrivateResourceTest"],
    tags = COMMON_TAGS + [
        "qa_fast",
        "qa_unreliable",  # b/111889561
        "qa_fast_unreliable",  # b/111889561
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "ChangeLibModSettingsTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ] + glob([
        "testData/PrivateResource/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.ChangeLibModSettingsTest"],
    tags = COMMON_TAGS + [
        "qa_fast",
        "qa_unreliable",  # b/111889561
        "qa_fast_unreliable",  # b/111889561
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "NameWithSpaceAndDollarTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.NameWithSpaceAndDollarTest"],
    tags = COMMON_TAGS + [
        "qa_fast",
        "qa_unreliable",  # b/111889561
        "qa_fast_unreliable",  # b/111889561
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "ThemeEditorTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/sdk:platforms/android-25",
    ] + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.ThemeEditorTest"],
    tags = COMMON_TAGS + [
        "qa_fast",
        "qa_unreliable",  # b/115685973
        "qa_fast_unreliable",  # b/115685973
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "ConstraintLayoutResizeHandleTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data:native_libs",
    ] + glob([
        "testData/LayoutTest/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.ConstraintLayoutResizeHandleTest"],
    tags = COMMON_TAGS + [
        "qa_fast",
        "qa_unreliable",  # b/115685973
        "qa_fast_unreliable",  # b/115685973
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "ConstraintLayoutAnchorExemptionTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data:native_libs",
    ] + glob([
        "testData/LayoutTest/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.ConstraintLayoutAnchorExemptionTest"],
    tags = COMMON_TAGS + [
        "qa_fast",
        "qa_unreliable",  # b/116793860
        "qa_fast_unreliable",  # b/116793860
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "ClearConstraintTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data:native_libs",
    ] + glob([
        "testData/LayoutTest/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.ClearConstraintTest"],
    tags = COMMON_TAGS + [
        "qa_fast",
        "qa_unreliable",  # b/116801839
        "qa_fast_unreliable",  # b/116801839
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "CreateBaselineConnectionTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data:native_libs",
    ] + glob([
        "testData/LayoutTest/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.CreateBaselineConnectionTest"],
    tags = COMMON_TAGS + [
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "AdaptiveIconsTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data:native_libs",
    ] + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.assetstudio.AdaptiveIconsTest"],
    tags = COMMON_TAGS + [
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "AndroidVectorDrawableToolTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data:native_libs",
    ] + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.AndroidVectorDrawableToolTest"],
    tags = COMMON_TAGS + [
        "qa_fast",
        "qa_unreliable",  # b/115539594
        "qa_fast_unreliable",  # b/115539594
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "ImageAssetGradleTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data:native_libs",
    ] + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.ImageAssetGradleTest"],
    tags = COMMON_TAGS + [
        "qa_fast",
        "qa_unreliable",  # b/115567466
        "qa_fast_unreliable",  # b/115567466
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "ImageAssetErrorCheckTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data:native_libs",
    ] + glob([
        "testData/SimpleApplication/**",
        "testData/TestImages/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.uibuilder.ImageAssetErrorCheckTest"],
    tags = COMMON_TAGS + [
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "AddCppToModuleActionTest",
    size = "large",
    data = COMMON_DATA,
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.cpp.AddCppToModuleActionTest"],
    tags = COMMON_TAGS + [
        "qa_sanity",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

java_test(
    name = "ShowFilesUnknownToCMakeActionTest",
    size = "large",
    data = COMMON_DATA,
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.cpp.ShowFilesUnknownToCMakeActionTest"],
    tags = COMMON_TAGS + [
        "qa_sanity",
        "no_crostini",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "CLionIntegrationTest",
    data = COMMON_DATA + glob([
        "testData/debugger/CLionNdkHelloJni/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.cpp.CLionIntegrationTest"],
    tags = COMMON_TAGS + [
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "ShortcutNavigationTest",
    data = COMMON_DATA + glob([
        "testData/debugger/NdkHelloJni/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.cpp.ShortcutNavigationTest"],
    tags = COMMON_TAGS + [
        "qa_fast",
        "qa_unreliable",  # b/115567466
        "qa_fast_unreliable",  # b/115567466
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "FlavorsEditingTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/sdk:platforms/android-24",
        "//prebuilts/studio/sdk:platforms/android-25",
    ] + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.projectstructure.FlavorsEditingTest"],
    tags = COMMON_TAGS + [
        "qa_fast",
        "qa_unreliable",  # b/111997969
        "qa_fast_unreliable",  # b/111997969
        "no-remote",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "ConvertFrom9PatchTest",
    data = COMMON_DATA + glob([
        "testData/ConvertFrom9Patch/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.webp.ConvertFrom9PatchTest"],
    tags = COMMON_TAGS + [
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "LintCheckWithKotlinTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
    ] + glob([
        "testData/KotlinInstrumentation/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.kotlin.LintCheckWithKotlinTest"],
    tags = COMMON_TAGS + [
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "NoGradleSyncForProjectReimportTest",
    data = COMMON_DATA + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.gradle.NoGradleSyncForProjectReimportTest"],
    tags = COMMON_TAGS + [
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "ApkViewerTest",
    data = COMMON_DATA + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.editors.ApkViewerTest"],
    tags = COMMON_TAGS + [
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "BuildAndRunCMakeProjectTest",
    size = "enormous",
    data = COMMON_DATA + glob([
        "testData/debugger/BasicCmakeAppForUI/**",
    ]) + select({
        ":has_emulator": [
            "@externsdk//:system-images",
            "@externsdk//:emulator",
        ],
        "//conditions:default": [],
    }),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.debugger.BuildAndRunCMakeProjectTest"],
    tags = [
        "block-network",
        "cpu:100",
        "no-remote",
        "no_mac",
        "no_windows",
        "qa_fast",
        "qa_fast_emu",
        "requires-fakeroot",
        "requires_emulator",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "JavaDebuggerTest",
    size = "enormous",
    data = COMMON_DATA + glob([
        "testData/debugger/BasicCmakeAppForUI/**",
    ]) + select({
        ":has_emulator": [
            "@externsdk//:system-images",
            "@externsdk//:emulator",
        ],
        "//conditions:default": [],
    }),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.debugger.JavaDebuggerTest"],
    tags = [
        "block-network",
        "cpu:100",
        "no-remote",
        "no_mac",
        "no_windows",
        "qa_fast",
        "qa_fast_emu",
        "requires-fakeroot",
        "requires_emulator",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "AutoDebuggerInNdkProjectTest",
    size = "enormous",
    data = COMMON_DATA + glob([
        "testData/debugger/NdkHelloJni/**",
    ]) + select({
        ":has_emulator": [
            "@externsdk//:system-images",
            "@externsdk//:emulator",
        ],
        "//conditions:default": [],
    }) + [
        "//prebuilts/tools:lldb",
        "//prebuilts/python/linux-x86:linux-x86",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.debugger.AutoDebuggerInNdkProjectTest"],
    tags = [
        "block-network",
        "cpu:100",
        "no-remote",
        "no_mac",
        "no_windows",
        "qa_fast",
        "qa_fast_emu",
        "qa_fast_unreliable_emu",
        "qa_unreliable",
        "requires-fakeroot",
        "requires_emulator",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "DualDebuggerInNdkProjectTest",
    size = "enormous",
    data = COMMON_DATA + glob([
        "testData/debugger/NdkHelloJni/**",
    ]) + select({
        ":has_emulator": [
            "@externsdk//:system-images",
            "@externsdk//:emulator",
        ],
        "//conditions:default": [],
    }) + [
        "//prebuilts/tools:lldb",
        "//prebuilts/python/linux-x86:linux-x86",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.debugger.DualDebuggerInNdkProjectTest"],
    tags = [
        "block-network",
        "cpu:100",
        "no-remote",
        "no_mac",
        "no_windows",
        "qa_fast",
        "qa_fast_emu",
        "qa_fast_unreliable_emu",
        "qa_unreliable",
        "requires-fakeroot",
        "requires_emulator",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "NativeDebuggerInNdkProjectTest",
    size = "enormous",
    data = COMMON_DATA + glob([
        "testData/debugger/NdkHelloJni/**",
    ]) + select({
        ":has_emulator": [
            "@externsdk//:system-images",
            "@externsdk//:emulator",
        ],
        "//conditions:default": [],
    }) + [
        "//prebuilts/tools:lldb",
        "//prebuilts/python/linux-x86:linux-x86",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.debugger.NativeDebuggerInNdkProjectTest"],
    tags = [
        "block-network",
        "cpu:100",
        "no-remote",
        "no_mac",
        "no_windows",
        "qa_fast",
        "qa_fast_emu",
        "qa_fast_unreliable_emu",
        "qa_unreliable",
        "requires-fakeroot",
        "requires_emulator",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "WatchpointTest",
    size = "enormous",
    data = COMMON_DATA + glob([
        "testData/debugger/WatchpointTestAppForUI/**",
    ]) + select({
        ":has_emulator": [
            "@externsdk//:system-images",
            "@externsdk//:emulator",
        ],
        "//conditions:default": [],
    }) + [
        "//prebuilts/tools:lldb",
        "//prebuilts/python/linux-x86:linux-x86",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.debugger.WatchpointTest"],
    tags = [
        "block-network",
        "cpu:100",
        "no-remote",
        "no_mac",
        "no_windows",
        "qa_fast",
        "qa_fast_emu",
        "qa_fast_unreliable_emu",
        "qa_unreliable",
        "requires-fakeroot",
        "requires_emulator",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "NativeDebuggerBreakpointsTest",
    size = "enormous",
    data = COMMON_DATA + glob([
        "testData/debugger/BasicCmakeAppForUI/**",
    ]) + select({
        ":has_emulator": [
            "@externsdk//:system-images",
            "@externsdk//:emulator",
        ],
        "//conditions:default": [],
    }) + [
        "//prebuilts/tools:lldb",
        "//prebuilts/python/linux-x86:linux-x86",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.debugger.NativeDebuggerBreakpointsTest"],
    tags = [
        "block-network",
        "cpu:100",
        "no-remote",
        "no_mac",
        "no_windows",
        "qa_fast",
        "qa_fast_emu",
        "qa_fast_unreliable_emu",  # b/137669728
        "qa_unreliable",  # b/137669728
        "requires-fakeroot",
        "requires_emulator",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "SessionRestartTest",
    size = "enormous",
    data = COMMON_DATA + glob([
        "testData/debugger/BasicCmakeAppForUI/**",
    ]) + select({
        ":has_emulator": [
            "@externsdk//:system-images",
            "@externsdk//:emulator",
        ],
        "//conditions:default": [],
    }) + [
        "//prebuilts/tools:lldb",
        "//prebuilts/python/linux-x86:linux-x86",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.debugger.SessionRestartTest"],
    tags = [
        "block-network",
        "cpu:100",
        "no-remote",
        "no_mac",
        "no_windows",
        "qa_fast",
        "qa_fast_emu",
        "qa_fast_unreliable_emu",  # b/137669728
        "qa_unreliable",  # b/137669728
        "requires-fakeroot",
        "requires_emulator",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "SmartStepIntoTest",
    size = "enormous",
    data = COMMON_DATA + glob([
        "testData/debugger/SmartStepIntoCmakeApp/**",
    ]) + select({
        ":has_emulator": [
            "@externsdk//:system-images",
            "@externsdk//:emulator",
        ],
        "//conditions:default": [],
    }) + [
        "//prebuilts/tools:lldb",
        "//prebuilts/python/linux-x86:linux-x86",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.debugger.SmartStepIntoTest"],
    tags = [
        "block-network",
        "cpu:100",
        "no-remote",
        "no_mac",
        "no_windows",
        "qa_fast",
        "qa_fast_emu",
        "qa_fast_unreliable_emu",  # b/138743860
        "qa_unreliable",  # b/138743860
        "requires-fakeroot",
        "requires_emulator",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "NdkSxsTest",
    data = COMMON_DATA,
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.cpp.NdkSxsTest"],
    tags = COMMON_TAGS + [
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "JavaToKotlinConversionTest",
    data = COMMON_DATA + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.kotlin.JavaToKotlinConversionTest"],
    tags = COMMON_TAGS + [
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "CreateFromPreexistingApkTest",
    data = COMMON_DATA + glob([
        "testData/ApkDebug/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.debugger.CreateFromPreexistingApkTest"],
    tags = COMMON_TAGS + [
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "CreateAPKProjectTest",
    size = "enormous",
    data = COMMON_DATA + glob([
        "testData/ApkDebug/**",
    ]) + select({
        ":has_emulator": [
            "@externsdk//:system-images",
            "@externsdk//:emulator",
        ],
        "//conditions:default": [],
    }) + [
        "//prebuilts/tools:lldb",
        "//prebuilts/python/linux-x86:linux-x86",
    ],
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.debugger.CreateAPKProjectTest"],
    tags = [
        "block-network",
        "cpu:100",
        "no-remote",
        "no_mac",
        "no_windows",
        "qa_fast",
        "qa_fast_emu",
        "requires-fakeroot",
        "requires_emulator",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "InstantAppRunFromCmdLineTest",
    size = "enormous",
    data = COMMON_DATA + glob([
        "testData/TopekaInstantApp/**",
    ]) + select({
        ":has_emulator": [
            "@externsdk//:system-images",
            "@externsdk//:emulator",
        ],
        "//conditions:default": [],
    }),
    jvm_flags = [
        "-Dtest.suite.class=com.android.tools.idea.tests.gui.instantapp.InstantAppRunFromCmdLineTest",
    ],
    # do not sort: the beginning of this list is copied from COMMON_TAGS
    tags = [
        "block-network",
        "no_mac",
        "no_windows",
        "no-remote",
        "requires-fakeroot",
        "qa_fast",
        "qa_fast_emu",
        "requires_emulator",
        "cpu:100",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "ImportAndRunInstantAppTest",
    size = "enormous",
    data = COMMON_DATA + glob([
        "testData/InstantAppsService/**",
    ]) + select({
        ":has_emulator": [
            "@externsdk//:system-images",
            "@externsdk//:emulator",
        ],
        "//conditions:default": [],
    }),
    jvm_flags = [
        "-Dtest.suite.class=com.android.tools.idea.tests.gui.instantapp.ImportAndRunInstantAppTest",
    ],
    # do not sort: the beginning of this list is copied from COMMON_TAGS
    tags = [
        "block-network",
        "no_mac",
        "no_windows",
        "slow",
        "no-remote",
        "requires-fakeroot",
        "qa_fast",
        "qa_fast_emu",
        "requires_emulator",
        "cpu:100",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fast test
java_test(
    name = "InstrumentationTest",
    size = "enormous",
    data = COMMON_DATA + glob([
        "testData/KotlinInstrumentation/**",
    ]) + select({
        ":has_emulator": [
            "@externsdk//:system-images",
            "@externsdk//:emulator",
        ],
        "//conditions:default": [],
    }),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.kotlin.InstrumentationTest"],
    tags = [
        "block-network",
        "cpu:100",
        "no-remote",
        "no_mac",
        "no_windows",
        "qa_fast",
        "qa_fast_emu",
        "requires-fakeroot",
        "requires_emulator",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fat test
java_test(
    name = "InferNullityTest",
    data = COMMON_DATA + glob([
        "testData/Nullity/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.editors.InferNullityTest"],
    tags = COMMON_TAGS + [
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fat test
java_test(
    name = "AddAppCompatLibTest",
    data = COMMON_DATA + glob([
        "testData/NoAppCompatLibApp/**",
    ]),
    jvm_flags = [
        "-Dtest.suite.class=com.android.tools.idea.tests.gui.projectstructure.AddAppCompatLibTest",
        "-Daspects.agent.jar=$(location //tools/base/aspects_agent:aspects_agent)",
        "-Daspects.agent.rules=$(location //tools/adt/idea/android-uitests:aspect_rules)",
        "-Daspects.agent.baseline=$(location //tools/adt/idea/android-uitests:aspects_baseline)",
    ],
    tags = COMMON_TAGS + [
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fat test
java_test(
    name = "CreateLoginActivityTest",
    data = COMMON_DATA,
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.npw.CreateLoginActivityTest"],
    tags = COMMON_TAGS + [
        "qa_fast",
        "qa_unreliable",  # b/117238132
        "qa_fast_unreliable",  # b/117238132
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fat test
java_test(
    name = "CreateSettingsActivityTest",
    data = COMMON_DATA,
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.npw.CreateSettingsActivityTest"],
    tags = COMMON_TAGS + [
        "qa_fast",
        "qa_unreliable",  # b/115679328
        "qa_fast_unreliable",  # b/115679328
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fat test
java_test(
    name = "AddLocaleTest",
    data = COMMON_DATA + [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data:native_libs",
    ] + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.editors.translations.AddLocaleTest"],
    tags = COMMON_TAGS + [
        "qa_fast",
        "qa_unreliable",  # b/115679328
        "qa_fast_unreliable",  # b/115679328
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fat test
java_test(
    name = "SurroundWithShortcutTest",
    data = COMMON_DATA + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.editors.SurroundWithShortcutTest"],
    tags = COMMON_TAGS + [
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fat test
java_test(
    name = "ForeachLiveTemplateTest",
    data = COMMON_DATA + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.editors.ForeachLiveTemplateTest"],
    tags = COMMON_TAGS + [
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fat test
java_test(
    name = "ForiLiveTemplateTest",
    data = COMMON_DATA + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.editors.ForiLiveTemplateTest"],
    tags = COMMON_TAGS + [
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)

# fat test
java_test(
    name = "ToastLiveTemplateTest",
    data = COMMON_DATA + glob([
        "testData/SimpleApplication/**",
    ]),
    jvm_flags = ["-Dtest.suite.class=com.android.tools.idea.tests.gui.editors.ToastLiveTemplateTest"],
    tags = COMMON_TAGS + [
        "qa_fast",
    ],
    test_class = "com.android.tools.idea.tests.gui.GuiJarTestSuite",
    runtime_deps = [":intellij.android.guiTests_testlib"],
)
