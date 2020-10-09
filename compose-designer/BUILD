load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build [unb]
iml_module(
    name = "intellij.android.compose-designer",
    srcs = ["src"],
    bundled_deps = ["//tools/adt/idea/compose-designer:unb.ui-animation-tooling-internal"],
    iml_files = ["intellij.android.compose-designer.iml"],
    lint_baseline = "lint_baseline.xml",
    project = "unb",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/designer:intellij.android.designer[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[module]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/layout-ui:intellij.android.layout-ui[module]",
        "//tools/adt/idea/.idea/libraries:unb.studio-analytics-proto",
        "//tools/adt/idea/.idea/libraries:unb.protobuf",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/adt/idea/compose-designer:unb.ui-animation-tooling-internal",
    ],
)

# managed by go/iml_to_build [unb]
iml_module(
    name = "intellij.android.compose-designer.tests",
    iml_files = ["intellij.android.compose-designer.tests.iml"],
    project = "unb",
    resources = ["testSrc/META-INF"],
    split_test_targets = {
        "gradle": {
            "tags": ["cpu:3"],
            "shard_count": 2,
            "test_filter": "com.android.tools.idea.compose.gradle",
        },
        "non-gradle": {},
    },
    tags = ["no_test_windows"],
    test_class = "com.android.tools.idea.compose.preview.ComposePreviewTestSuite",
    test_data = [
        "//prebuilts/studio/jdk",
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platforms/latest",
        "//prebuilts/tools/common/kotlin-plugin:Kotlin/kotlinc/build.txt",
        "//tools/adt/idea/android:test_deps.zip",
        "//tools/adt/idea/android/testData",
        "//tools/adt/idea/compose-designer/testData",
        "//tools/base/build-system:gradle-distrib",
        "//tools/base/build-system:studio_repo.zip",
        "//tools/base/third_party/kotlin:kotlin-m2repository.zip",
    ],
    test_resources = ["testData"],
    test_runtime_deps = ["//tools/adt/idea/databinding:intellij.android.databinding"],
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
        "//tools/adt/idea/deploy:intellij.android.deploy[module, test]",
        "//tools/adt/idea/.idea/libraries:unb.mockito[test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module, test]",
        "//tools/adt/idea/designer:intellij.android.designer[module, test]",
        "//tools/adt/idea/layout-ui:intellij.android.layout-ui[module, test]",
        "//tools/adt/idea/compose-designer:unb.ui-animation-tooling-internal[test]",
        "//tools/adt/idea/.idea/libraries:unb.studio-analytics-proto",
        "//tools/adt/idea/.idea/libraries:unb.protobuf",
    ],
)

# managed by go/iml_to_build [unb]
java_import(
    name = "unb.ui-animation-tooling-internal",
    jars = ["lib/ui-animation-tooling-internal.jar"],
    tags = [
        "manual",
        "unb",
    ],
    visibility = ["//visibility:public"],
)
