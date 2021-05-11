load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.nav.editor",
    srcs = ["src"],
    iml_files = ["intellij.android.nav.editor.iml"],
    lint_baseline = "lint_baseline.xml",
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/adt/idea/assistant[module]",
        "//tools/adt/idea/designer:intellij.android.designer[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/adt/idea/layout-ui:intellij.android.layout-ui[module]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/nav/editor:eclipse-layout-kernel",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module]",
        "//tools/adt/idea/observable:intellij.android.observable[module]",
        "//tools/adt/idea/observable-ui:intellij.android.observable.ui[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.nav.editor.tests",
    iml_files = ["intellij.android.nav.editor.tests.iml"],
    tags = [
        "no_test_mac",  # b/156004626
        "no_test_windows",
    ],
    test_class = "com.android.tools.idea.naveditor.NavEditorTestSuite",
    test_data = [
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//tools/adt/idea/android/testData",
        "//tools/adt/idea/designer/testData",
        "//tools/adt/idea/android:test_deps",
        "//prebuilts/studio/sdk:platforms/latest",
    ] + glob(["testData/**"]),
    test_srcs = ["testSrc"],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/adt/idea/designer:intellij.android.designer.tests[module, test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/analytics-library/shared:analytics-shared[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/nav/editor:intellij.android.nav.editor[module, test]",
        "//tools/adt/idea/designer:intellij.android.designer[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto[test]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module, test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module, test]",
        "//tools/adt/idea/layout-ui:intellij.android.layout-ui[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module, test]",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module, test]",
    ],
)

# managed by go/iml_to_build
java_import(
    name = "eclipse-layout-kernel",
    # do not sort: must match IML order
    jars = [
        "//prebuilts/tools/common/m2/repository/org/eclipse/elk/org.eclipse.elk.alg.layered/0.3.0:eclipse-layout-kernel_files",
        "//prebuilts/tools/common/m2/repository/org/eclipse/elk/org.eclipse.elk.core/0.3.0:eclipse-layout-kernel_files",
        "//prebuilts/tools/common/m2/repository/org/eclipse/elk/org.eclipse.elk.graph/0.3.0:eclipse-layout-kernel_files",
        "//prebuilts/tools/common/m2/repository/org/eclipse/emf/org.eclipse.emf.common/2.12.0:eclipse-layout-kernel_files",
        "//prebuilts/tools/common/m2/repository/org/eclipse/emf/org.eclipse.emf.ecore/2.12.0:eclipse-layout-kernel_files",
    ],
    visibility = ["//visibility:public"],
)
