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
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "@intellij//:intellij-sdk",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/nav/safeargs:intellij.android.nav.safeargs",
        "//tools/adt/idea/nav/safeargs/common:intellij.android.nav.safeargs.common",
        "//tools/adt/idea/nav/safeargs/k2:intellij.android.nav.safeargs.k2",
        "//tools/adt/idea/nav/safeargs/tests/common:intellij.android.nav.safeargs.tests.common",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
    ],
    test_srcs = ["src"],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = ["@intellij//:com.intellij.java"],
)
