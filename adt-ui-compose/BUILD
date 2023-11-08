load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.adt.ui.compose",
    srcs = ["src"],
    iml_files = ["intellij.android.adt.ui.compose.iml"],
    kotlin_use_compose = True,
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//prebuilts/tools/common/m2:jewel-ide",
        "//prebuilts/tools/common/m2:compose-desktop",
        "//prebuilts/tools/common/m2:jewel-standalone",
        "//prebuilts/tools/common/m2:compose-desktop-test",
    ],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//prebuilts/tools/common/m2:jewel-ide",
        "//prebuilts/tools/common/m2:compose-desktop",
        "//prebuilts/tools/common/m2:jewel-standalone[test]",
        "//prebuilts/tools/common/m2:compose-desktop-test[test]",
    ],
)
