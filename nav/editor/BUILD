load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_repo")

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
        "//prebuilts/tools/common/m2:eclipse-layout-kernel",
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
        ":test_deps",
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//tools/adt/idea/android/testData",
        "//tools/adt/idea/designer/testData",
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

maven_repo(
    name = "test_deps",
    # keep sorted: for buildifier
    artifacts = [
        "//prebuilts/tools/common/m2:android.arch.navigation.navigation-common.1.0.0_aar",
        "//prebuilts/tools/common/m2:android.arch.navigation.navigation-fragment.1.0.0_aar",
        "//prebuilts/tools/common/m2:android.arch.navigation.navigation-runtime.1.0.0_aar",
        "//prebuilts/tools/common/m2:androidx.fragment.fragment.1.3.4_aar",
        "//prebuilts/tools/common/m2:androidx.navigation.navigation-common.2.3.5_aar",
        "//prebuilts/tools/common/m2:androidx.navigation.navigation-fragment.2.3.5_aar",
        "//prebuilts/tools/common/m2:androidx.navigation.navigation-runtime.2.3.5_aar",
        "//prebuilts/tools/common/m2:com.android.support.support-fragment.28.0.0_aar",
        "//prebuilts/tools/common/m2:com.google.jimfs.jimfs.1.1",
        "//prebuilts/tools/common/m2:com.sun.activation.javax.activation.1.2.0",
        "//prebuilts/tools/common/m2:javax.inject.javax.inject.1",
        "//prebuilts/tools/common/m2:junit.junit.4.12",
        "//prebuilts/tools/common/m2:org.jetbrains.kotlin.kotlin-stdlib-jdk7.1.4.32",
        "//prebuilts/tools/common/m2:xmlpull.xmlpull.1.1.3.1",
    ],
)
