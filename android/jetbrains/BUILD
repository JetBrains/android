load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.core.jetbrains",
    generate_k1_tests = True,
    iml_files = ["intellij.android.core.jetbrains.iml"],
    test_class = "com.android.tools.idea.IdeaTestSuite",
    # keep sorted
    test_data = [
        "//prebuilts/studio/jdk/jdk11:jdk_runtime_files",
        "//prebuilts/studio/layoutlib:runtime",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:layoutlib_extensions",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android:test_deps",
        "//tools/adt/idea/android/testData",
        "//tools/base/build-system:android_gradle_plugin_runtime_dependencies",
        "//tools/base/resource-repository/test/resources",
    ],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/base/lint:studio.android.sdktools.lint-checks",
        "//tools/adt/idea/android:intellij.android.core.tests",
        "//tools/base/resource-repository:studio.android.sdktools.resource-repository",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/.idea/libraries:kotlin-test",
        "//tools/adt/idea/project-system-gradle-models:intellij.android.projectSystem.gradle.models",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/adt/idea/android-navigator:intellij.android.navigator",
        "//tools/adt/idea/rendering:intellij.android.rendering",
        "//tools/adt/idea/layoutlib-loader:intellij.android.layoutlib-loader",
        "//tools/adt/idea/jps-model:intellij.android.jps.model",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/android/editing/documentation:intellij.android.core.editing.documentation",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
    ],
    test_friends = ["//tools/adt/idea/android:intellij.android.core"],
    test_jvm_flags = [
        "-Djdk.attach.allowAttachSelf=true",
    ],
    test_shard_count = 6,
    test_srcs = ["testSrc"],
    test_tags = [
        "block-network",
    ],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/project-system-gradle-psd:intellij.android.projectSystem.gradle.psd",
        "@intellij//:org.jetbrains.kotlin",
    ],
)
