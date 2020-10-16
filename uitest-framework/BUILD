load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_repo")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.guiTestFramework",
    srcs = ["src"],
    iml_files = ["intellij.android.guiTestFramework.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    tags = ["no_test_windows"],
    test_class = "com.android.tools.idea.tests.gui.framework.GuiTestFrameworkTestSuite",
    test_data = [
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle",
        "//tools/base/common:studio.android.sdktools.common[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/swing-testing/fest-swing[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/android/lib:spantable[test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module, test]",
        "//tools/adt/idea/layout-ui:intellij.android.layout-ui[module, test]",
        "//tools/adt/idea/designer:intellij.android.designer[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/studio/google/cloud/testing/test-recorder[module, test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/base/perf-logger:studio.perf-logger[module, test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module, test]",
        "//tools/adt/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/adt/idea/android-layout-inspector[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/profilers-android:intellij.android.profilersAndroid[module, test]",
        "//tools/base/layoutinspector:studio.android.sdktools.layoutinspector[module, test]",
        "//tools/adt/idea/nav/editor:intellij.android.nav.editor[module, test]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module, test]",
        "//tools/adt/idea/deploy:intellij.android.deploy[module, test]",
        "//tools/adt/idea/layout-inspector:intellij.android.layout-inspector[module, test]",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl[module]",
        "//tools/adt/idea/bleak:intellij.android.bleak[module, test]",
        "//tools/adt/idea/project-system-gradle-psd:intellij.android.projectSystem.gradle.psd[module]",
        "//tools/base/wizard/template-plugin:studio.intellij.android.wizardTemplate.plugin[module, test]",
        "//tools/base/wizard/template-impl:studio.intellij.android.wizardTemplate.impl[module, test]",
        "//tools/adt/idea/android-plugin:intellij.android.plugin[module, test]",
        "//tools/adt/idea/emulator:intellij.android.emulator[module, test]",
    ],
)

# Dependencies of UI tests not covered by //tools/adt/idea/android:test_deps or out/studio/repo. This
# allows us to run UI tests on a release bundle without a source checkout.
maven_repo(
    name = "uitest_deps",
    artifacts = [
        "//prebuilts/tools/common/m2/repository/android/arch/lifecycle/runtime/1.0.0:aar",
        "//prebuilts/tools/common/m2/repository/androidx/constraintlayout/constraintlayout/1.1.2:aar",
        "//prebuilts/tools/common/m2/repository/androidx/constraintlayout/constraintlayout-solver/1.1.2:jar",
        "//prebuilts/tools/common/m2/repository/androidx/leanback/leanback/1.0.0:aar",
        "//prebuilts/tools/common/m2/repository/androidx/legacy/legacy-support-v4/1.0.0:aar",
        "//prebuilts/tools/common/m2/repository/androidx/media/media/1.0.0:aar",
        "//prebuilts/tools/common/m2/repository/androidx/percentlayout/percentlayout/1.0.0:aar",
        "//prebuilts/tools/common/m2/repository/androidx/recyclerview/recyclerview/1.0.0:aar",
        "//prebuilts/tools/common/m2/repository/androidx/wear/wear/1.0.0:aar",
        "//prebuilts/tools/common/m2/repository/androidx/test/espresso/espresso-core/3.1.0:aar",
        "//prebuilts/tools/common/m2/repository/androidx/test/espresso/espresso-idling-resource/3.1.0:aar",
        "//prebuilts/tools/common/m2/repository/androidx/test/monitor/1.1.0:aar",
        "//prebuilts/tools/common/m2/repository/androidx/test/runner/1.1.0:aar",
        "//prebuilts/tools/common/m2/repository/com/android/support/mediarouter-v7/27.1.1:aar",
        "//prebuilts/tools/common/m2/repository/com/google/android/material/material/1.0.0:aar",
    ],
    visibility = ["//visibility:public"],
)
