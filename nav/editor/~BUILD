load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_repository")

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
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/assistant",
        "//tools/adt/idea/designer:intellij.android.designer",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/layout-ui:intellij.android.layout-ui",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//prebuilts/tools/common/m2:eclipse-layout-kernel",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model",
        "//tools/adt/idea/observable:intellij.android.observable",
        "//tools/adt/idea/observable-ui:intellij.android.observable.ui",
        "//tools/adt/idea/android-npw:intellij.android.newProjectWizard",
        "//tools/adt/idea/rendering:intellij.android.rendering",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.nav.editor.tests",
    iml_files = ["intellij.android.nav.editor.tests.iml"],
    tags = [
        "noci:studio-win",
    ],
    test_class = "com.android.tools.idea.naveditor.NavEditorTestSuite",
    test_data = [
        ":test_deps",
        "//prebuilts/studio/jdk/jdk11:jdk_runtime_files",
        "//prebuilts/studio/layoutlib:runtime",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/testData",
        "//tools/adt/idea/designer/testData",
    ] + glob(["testData/**"]),
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/.idea/libraries:kotlin-test",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/analytics-library/shared:analytics-shared",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/nav/editor:intellij.android.nav.editor",
        "//tools/adt/idea/designer:intellij.android.designer",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/layout-ui:intellij.android.layout-ui",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model",
        "//tools/adt/idea/rendering:intellij.android.rendering",
        "//tools/adt/idea/designer:intellij.android.designer.tests",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
    ],
    test_friends = ["//tools/adt/idea/nav/editor:intellij.android.nav.editor"],
    test_srcs = ["testSrc"],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:com.intellij.gradle",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/layoutlib:intellij.android.layoutlib",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
    ],
)

maven_repository(
    name = "test_deps",
    artifacts = [
        "@maven//:android.arch.navigation.navigation-fragment_1.0.0",
        "@maven//:androidx.navigation.navigation-fragment_2.3.5",
        "@maven//:com.google.jimfs.jimfs_1.1",
        "@maven//:com.sun.activation.javax.activation_1.2.0",
        "@maven//:javax.inject.javax.inject_1",
        "@maven//:junit.junit_4.12",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk7_1.4.32",
        "@maven//:xmlpull.xmlpull_1.1.3.1",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.nav.editor.gradle",
    srcs = ["gradle/src"],
    iml_files = ["gradle/intellij.android.nav.editor.gradle.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/nav/editor:intellij.android.nav.editor",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/designer:intellij.android.designer",
    ],
)
