load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "studio-flags",
    srcs = ["src"],
    iml_files = ["studio-flags.iml"],
    # do not sort: must match IML order
    resources = [
        "resources",
        "flags/resources",
    ],
    visibility = ["//visibility:public"],
    exports = ["//tools/adt/idea/server-flags:intellij.android.server-flags"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/.idea/libraries:agp-version",
        "//tools/adt/idea/server-flags:intellij.android.server-flags",
        "@intellij//:intellij-sdk",
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/base/flags:studio.android.sdktools.flags",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "studio-flags-tests",
    iml_files = ["studio-flags-tests.iml"],
    test_class = "com.android.tools.idea.flags.StudioFlagsTestSuite",
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/studio-flags",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/server-flags:intellij.android.server-flags",
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/adt/idea/.idea/libraries:kotlin-test",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
    ],
    test_srcs = ["test"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:org.jetbrains.kotlin",
    ],
)
