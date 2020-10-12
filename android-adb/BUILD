load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_repo")

# managed by go/iml_to_build [unb]
iml_module(
    name = "intellij.android.adb",
    srcs = ["src"],
    iml_files = ["intellij.android.adb.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    project = "unb",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module]",
        "//tools/adt/idea/.idea/libraries:unb.kotlin-stdlib-jdk8",
        "//tools/adt/idea/.idea/libraries:unb.Guava",
        "//tools/adt/idea/.idea/libraries:unb.zxing-core",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
    ],
)

# managed by go/iml_to_build [unb]
iml_module(
    name = "intellij.android.adb.tests",
    iml_files = ["intellij.android.adb.tests.iml"],
    project = "unb",
    test_class = "com.android.tools.idea.adb.AndroidAdbTestSuite",
    test_data = [
        "//prebuilts/studio/sdk:platform-tools",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/android-adb:intellij.android.adb[module, test]",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/.idea/libraries:unb.truth[test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/.idea/libraries:unb.zxing-core[test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module, test]",
        "//tools/adt/idea/.idea/libraries:unb.mockito[test]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module, test]",
    ],
)
