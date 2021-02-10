load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_repo")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.adb",
    srcs = ["src"],
    iml_files = ["intellij.android.adb.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module]",
        "//tools/adt/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/adt/idea/.idea/libraries:zxing-core",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.adb.tests",
    iml_files = ["intellij.android.adb.tests.iml"],
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
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/.idea/libraries:zxing-core[test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module, test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module, test]",
    ],
)
