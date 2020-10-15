load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build [unb]
iml_module(
    name = "intellij.android.emulator",
    srcs = ["src"],
    iml_files = ["intellij.android.emulator.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    project = "unb",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/base/deploy/deployer:studio.android.sdktools.deployer[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/android-adb:intellij.android.adb[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/adt/idea/designer:intellij.android.designer[module]",
        "//tools/adt/idea/layout-ui:intellij.android.layout-ui[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/.idea/libraries:unb.emulator-proto",
        "//tools/adt/idea/.idea/libraries:unb.Guava",
        "//tools/adt/idea/.idea/libraries:unb.kotlin-stdlib-jdk8",
        "//tools/adt/idea/.idea/libraries:unb.studio-grpc",
        "//tools/adt/idea/.idea/libraries:unb.studio-proto",
    ],
)

# managed by go/iml_to_build [unb]
iml_module(
    name = "intellij.android.emulator.tests",
    iml_files = ["intellij.android.emulator.tests.iml"],
    project = "unb",
    test_class = "com.android.tools.idea.emulator.EmbeddedEmulatorTestSuite",
    test_data = [
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/testData",
        "//tools/adt/idea/artwork:device-art-resources",
        "//tools/adt/idea/emulator/testData",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-webp",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module, test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/android-adb:intellij.android.adb[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module, test]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/emulator:intellij.android.emulator[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.extensions[module, test]",
        "//tools/adt/idea/.idea/libraries:unb.assertJ[test]",
        "//tools/adt/idea/.idea/libraries:unb.emulator-proto[test]",
        "//tools/adt/idea/.idea/libraries:unb.Guava[test]",
        "//tools/adt/idea/.idea/libraries:unb.jimfs[test]",
        "//tools/adt/idea/.idea/libraries:unb.mockito[test]",
        "//tools/adt/idea/.idea/libraries:unb.studio-grpc[test]",
        "//tools/adt/idea/.idea/libraries:unb.studio-proto[test]",
        "//tools/adt/idea/.idea/libraries:unb.truth[test]",
    ],
)
