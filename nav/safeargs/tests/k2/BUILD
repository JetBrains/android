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
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "@intellij//:intellij-sdk[test]",
        "@intellij//:com.intellij.java",
        "@intellij//:org.jetbrains.kotlin[test]",
        "//tools/adt/idea/nav/safeargs:intellij.android.nav.safeargs[test]",
        "//tools/adt/idea/nav/safeargs/common:intellij.android.nav.safeargs.common[test]",
        "//tools/adt/idea/nav/safeargs/k2:intellij.android.nav.safeargs.k2[test]",
        "//tools/adt/idea/nav/safeargs/tests/common:intellij.android.nav.safeargs.tests.common[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin[test]",
        "//tools/base/flags:studio.android.sdktools.flags[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[test]",
        "//tools/adt/idea/android-common:intellij.android.common[test]",
        "//tools/adt/idea/android:intellij.android.core[test]",
        "//tools/adt/idea/render-resources:intellij.android.render-resources[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[test]",
    ],
)
