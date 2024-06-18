load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.nav.safeargs.tests.k2",
    iml_files = ["intellij.android.nav.safeargs.tests.k2.iml"],
    tags = [
        "noci:studio-win",
    ],
    test_class = "com.android.tools.idea.nav.safeargs.kotlin.k2.SafeArgsK2TestSuite",
    # keep sorted
    test_data = [
        "//tools/adt/idea/nav/safeargs/testData",
    ],
    test_srcs = ["src"],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk[test]",
        "@intellij//:com.intellij.java",
        "@intellij//:org.jetbrains.kotlin[test]",
        "//tools/adt/idea/nav/safeargs:intellij.android.nav.safeargs[module, test]",
        "//tools/adt/idea/nav/safeargs/common:intellij.android.nav.safeargs.common[module, test]",
        "//tools/adt/idea/nav/safeargs/k2:intellij.android.nav.safeargs.k2[module, test]",
        "//tools/adt/idea/nav/safeargs/tests/common:intellij.android.nav.safeargs.tests.common[module, test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/render-resources:intellij.android.render-resources[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
    ],
)
