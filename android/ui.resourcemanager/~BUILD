load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.core.ui.resourcemanager",
    iml_files = ["intellij.android.core.ui.resourcemanager.iml"],
    test_class = "com.android.tools.idea.IdeaTestSuite",
    # keep sorted
    test_data = [
        "//prebuilts/studio/layoutlib:runtime",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/lib:sampleData",
        "//tools/adt/idea/android/testData",
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
        "//tools/adt/idea/android:intellij.android.core.tests",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/.idea/libraries:kotlin-test",
        "//tools/base/resource-repository:studio.android.sdktools.resource-repository",
        "//tools/adt/idea/.idea/libraries:guava-testlib",
        "//tools/adt/idea/rendering:intellij.android.rendering",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
    ],
    test_jvm_flags = [
        "-Djdk.attach.allowAttachSelf=true",
    ],
    test_shard_count = 2,
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
        "@intellij//:org.jetbrains.kotlin",
    ],
)
