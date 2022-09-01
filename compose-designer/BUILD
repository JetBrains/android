load("//tools/base/bazel:bazel.bzl", "iml_module", "iml_test")
load("//tools/base/bazel:maven.bzl", "maven_repository")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.compose-designer",
    # do not sort: must match IML order
    srcs = [
        "gen",
        "src",
    ],
    iml_files = ["intellij.android.compose-designer.iml"],
    lint_baseline = "lint_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/designer:intellij.android.designer[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module]",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[module]",
        "//tools/adt/idea/project-system-gradle-models:intellij.android.projectSystem.gradle.models[module]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/layout-ui:intellij.android.layout-ui[module]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/adt/idea/compose-designer:ui-animation-tooling-internal",
        "//tools/adt/idea/intellij.android.compose-common[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.compose-designer.tests",
    iml_files = ["intellij.android.compose-designer.tests.iml"],
    resources = ["testSrc/META-INF"],
    split_test_targets = {
        "gradle": {
            "tags": ["cpu:3"],
            "shard_count": 7,
            "test_filter": "com.android.tools.idea.compose.gradle",
        },
        "non-gradle": {},
    },
    tags = ["no_test_windows"],
    test_class = "com.android.tools.idea.compose.preview.ComposePreviewTestSuite",
    test_data = [
        ":test_deps",
        "//prebuilts/studio/jdk",
        "//prebuilts/studio/jdk/jdk11",
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/testData",
        "//tools/adt/idea/compose-designer/testData",
        "//tools/base/build-system:gradle-distrib",
        "//tools/base/build-system:android_gradle_plugin.zip",
        "//tools/base/build-system:android_gradle_plugin_runtime_dependencies",
        "//tools/adt/idea/compose-designer:kotlin-compiler-daemon-libs",
        "//tools/base/third_party/kotlin:kotlin-m2repository",
    ],
    test_friends = ["//tools/adt/idea/compose-designer:intellij.android.compose-designer"],
    test_jvm_flags = ["-Dcom.google.testing.junit.runner.shouldInstallTestSecurityManager=false"],
    test_resources = ["testData"],
    test_srcs = ["testSrc"],
    test_tags = [
        "block-network",
        "no_test_mac",  # b/72973198
    ],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:kotlin-test[test]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/compose-designer:intellij.android.compose-designer[module, test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/android:intellij.android.core.tests[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module, test]",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module, test]",
        "//tools/adt/idea/deploy:intellij.android.deploy[module, test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module, test]",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[module, test]",
        "//tools/adt/idea/designer:intellij.android.designer[module, test]",
        "//tools/adt/idea/layout-ui:intellij.android.layout-ui[module, test]",
        "//tools/adt/idea/compose-designer:ui-animation-tooling-internal[test]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/intellij.android.compose-common[module]",
        "//tools/adt/idea/compose-ide-plugin:intellij.android.compose-ide-plugin[module, test]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module, test]",
    ],
)

filegroup(
    name = "kotlin-compiler-daemon-libs",
    # We only include the 1.2.0 version as part of the distribution as it is the only one needed
    # for tests and canary.
    srcs = ["lib/kotlin-compiler-daemon-1.2.0-fallback.jar"],
    visibility = ["//visibility:public"],
)

load("//tools/base/bazel:jvm_import.bzl", "jvm_import")

# managed by go/iml_to_build
jvm_import(
    name = "ui-animation-tooling-internal",
    jars = ["lib/ui-animation-tooling-internal.jar"],
    visibility = ["//visibility:public"],
)

maven_repository(
    name = "test_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:androidx.annotation.annotation_1.2.0-rc01",
        "@maven//:androidx.appcompat.appcompat_1.3.0",
        "@maven//:androidx.compose.compiler.compiler_1.2.0",
        "@maven//:androidx.compose.ui.ui-tooling_1.2.0",
        "@maven//:androidx.core.core_1.5.0-beta01",
        "@maven//:androidx.core.core_1.5.0-rc02",
        "@maven//:androidx.lifecycle.lifecycle-common-java8_2.4.0",
        "@maven//:androidx.lifecycle.lifecycle-common_2.4.0",
        "@maven//:androidx.savedstate.savedstate_1.1.0-rc01",
        "@maven//:com.google.auto.value.auto-value_1.6.2",
        "@maven//:com.google.errorprone.error_prone_annotations_2.3.2",
        "@maven//:com.google.jimfs.jimfs_1.1",
        "@maven//:com.sun.activation.javax.activation_1.2.0",
        "@maven//:commons-lang.commons-lang_2.4",
        "@maven//:javax.inject.javax.inject_1",
        "@maven//:junit.junit_4.12",
        "@maven//:org.codehaus.mojo.animal-sniffer-annotations_1.17",
        "@maven//:org.hamcrest.hamcrest-library_1.3",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin-api_1.7.0",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_1.7.0",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect_1.4.32",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect_1.7.0",
        "@maven//:org.jetbrains.kotlin.kotlin-script-runtime_1.7.0",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk7_1.4.32",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.7.0",
        "@maven//:xmlpull.xmlpull_1.1.3.1",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.compose-designer.integration",
    enable_tests = False,
    iml_files = ["intellij.android.compose-designer.integration.iml"],
    test_srcs = ["integrationTestSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-junit",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/as-driver:as-driver.utils[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
    ],
)

compose_preview_data = select({
    "@platforms//os:linux": [
        "//tools/adt/idea/studio:android-studio.linux.zip",
    ],
    "@platforms//os:macos": [
        "//tools/adt/idea/studio:android-studio.mac.zip",
    ],
    "@platforms//os:windows": [
        "//tools/adt/idea/studio:android-studio.win.zip",
    ],
}) + [
    ":compose_preview_deps",
    "//prebuilts/studio/sdk:build-tools/latest",
    "//tools/adt/idea/compose-designer/testData:composepreview",
    "//tools/base/build-system:gradle-distrib-7.3.3",
]

maven_repository(
    name = "compose_preview_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:androidx.activity.activity-compose_1.3.1",
        "@maven//:androidx.compose.compiler.compiler_1.2.0",
        "@maven//:androidx.compose.material.material_1.2.0",
        "@maven//:androidx.compose.ui.ui-test-manifest_1.2.0",
        "@maven//:androidx.compose.ui.ui-tooling-preview_1.2.0",
        "@maven//:androidx.compose.ui.ui-tooling_1.2.0",
        "@maven//:androidx.compose.ui.ui_1.2.0",
        "@maven//:androidx.core.core-ktx_1.7.0",
        "@maven//:androidx.lifecycle.lifecycle-common-java8_2.4.0",
        "@maven//:androidx.lifecycle.lifecycle-runtime-ktx_2.3.1",
        "@maven//:com.android.tools.build.aapt2_7.1.0-7984345",
        "@maven//:com.android.tools.build.aapt2_7.2.0-7984345",
        "@maven//:com.android.tools.build.gradle_7.2.0",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin-api_1.7.0",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_1.7.0",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.7.0",
    ],
)

iml_test(
    name = "ComposePreviewKotlinTest",
    timeout = "long",
    data = compose_preview_data,
    module = ":intellij.android.compose-designer.integration",
    tags_linux = ["block-network"],
    tags_mac = [],
    tags_windows = ["block-network"],
    test_class = "com.android.tools.idea.compose.ComposePreviewKotlin",
)

iml_test(
    name = "ComposePreviewXmlTest",
    timeout = "long",
    data = compose_preview_data,
    module = ":intellij.android.compose-designer.integration",
    tags_linux = ["block-network"],
    tags_windows = ["block-network"],
    test_class = "com.android.tools.idea.compose.ComposePreviewXml",
)
