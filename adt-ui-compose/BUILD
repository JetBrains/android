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
        "//prebuilts/tools/common/m2:compose-desktop-animation",
        "//prebuilts/tools/common/m2:compose-desktop-material",
        "//prebuilts/tools/common/m2:compose-desktop-runtime",
        "//prebuilts/tools/common/m2:compose-desktop-foundation",
        "//prebuilts/tools/common/m2:compose-desktop-ui",
        "//prebuilts/tools/common/m2:skiko",
    ],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//prebuilts/tools/common/m2:compose-desktop-animation",
        "//prebuilts/tools/common/m2:compose-desktop-material",
        "//prebuilts/tools/common/m2:compose-desktop-runtime",
        "//prebuilts/tools/common/m2:compose-desktop-foundation",
        "//prebuilts/tools/common/m2:compose-desktop-ui",
        "//prebuilts/tools/common/m2:skiko",
    ],
)
