load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.nav.safeargs.tests.k1",
    iml_files = ["intellij.android.nav.safeargs.tests.k1.iml"],
    kotlinc_opts = ["-opt-in=org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi"],
    split_test_targets = {
        "gradle": {
            "tags": ["cpu:3"],
            "shard_count": 2,
            "test_filter": ".gradle",
            "data": [
                # keep sorted
                "//prebuilts/studio/sdk:build-tools/latest",
                "//prebuilts/studio/sdk:platforms/latest",
                "//tools/adt/idea/nav/safeargs/tests:testdeps_repo",
                "//tools/base/build-system:android_gradle_plugin.zip",
                "//tools/base/build-system:android_gradle_plugin_runtime_dependencies",
                "//tools/base/build-system:gradle-distrib",
                "//tools/base/build-system/integration-test:kotlin_gradle_plugin_prebuilts",
            ],
        },
        "non-gradle": {},
    },
    tags = [
        "no_k2",
        "noci:studio-win",
    ],
    test_class = "com.android.tools.idea.nav.safeargs.kotlin.k1.SafeArgsK1TestSuite",
    # keep sorted
    test_data = [
        "//tools/adt/idea/nav/safeargs/testData",
    ],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/nav/safeargs:intellij.android.nav.safeargs",
        "//tools/adt/idea/nav/safeargs/common:intellij.android.nav.safeargs.common",
        "//tools/adt/idea/nav/safeargs/k1:intellij.android.nav.safeargs.k1",
        "//tools/adt/idea/nav/safeargs/tests/common:intellij.android.nav.safeargs.tests.common",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
    ],
    test_srcs = ["src"],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:com.intellij.gradle",
        "@intellij//:org.jetbrains.kotlin",
    ],
)
