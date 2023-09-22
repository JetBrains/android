load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.adt.ui.compose",
    srcs = ["src"],
    iml_files = ["intellij.android.adt.ui.compose.iml"],
    kotlin_use_compose = True,
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/adt/idea/.idea/libraries:jewel",
        "//tools/adt/idea/.idea/libraries:compose-desktop",
        "//tools/adt/idea/.idea/libraries:compose-desktop-test",
    ],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/.idea/libraries:jewel",
        "//tools/adt/idea/.idea/libraries:compose-desktop",
        "//tools/adt/idea/.idea/libraries:compose-desktop-test[test]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
    ],
)
