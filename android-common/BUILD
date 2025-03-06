load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:jvm_import.bzl", "jvm_import")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.common",
    srcs = ["src"],
    generate_k1_tests = True,
    iml_files = ["intellij.android.common.iml"],
    lint_baseline = "lint_baseline.xml",
    tags = ["noci:studio-win"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/.idea/libraries:kotlin-test[test]",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/analytics-library/shared:analytics-shared",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api",
        "//tools/base/testutils:studio.android.sdktools.testutils[test]",
        "//tools/base/ninepatch:studio.android.sdktools.ninepatch",
        "//tools/base/sdklib:studio.android.sdktools.sdklib",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[test]",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/adt/idea/server-flags:intellij.android.server-flags",
        "//tools/adt/idea/jps-model:intellij.android.jps.model",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test[test]",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
        "//tools/adt/idea/android-common:libandroid-core-proto",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.common.tests",
    iml_files = ["intellij.android.common.tests.iml"],
    test_class = "com.android.tools.idea.AndroidCommonTestSuite",
    # keep sorted
    test_data = [
        "//tools/adt/idea/android-common/testData",
    ],
    test_srcs = ["test"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "//tools/adt/idea/.idea/libraries:kotlin-test[test]",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/analytics-library/shared:analytics-shared",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api",
        "//tools/base/testutils:studio.android.sdktools.testutils[test]",
        "//tools/base/ninepatch:studio.android.sdktools.ninepatch",
        "//tools/base/sdklib:studio.android.sdktools.sdklib",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[test]",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/adt/idea/server-flags:intellij.android.server-flags",
        "//tools/adt/idea/jps-model:intellij.android.jps.model",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test[test]",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
        "//tools/adt/idea/android-common:intellij.android.common[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[test]",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin[test]",
        "//tools/adt/idea/android-common:libandroid-core-proto[test]",
    ],
)

# managed by go/iml_to_build
jvm_import(
    name = "libandroid-core-proto",
    jars = ["//tools/adt/idea/android:libandroid-core-proto.jar"],
    visibility = ["//visibility:public"],
)
