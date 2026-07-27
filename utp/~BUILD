load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:jvm_import.bzl", "jvm_import")

# managed by go/iml_to_build
iml_module(
    name = "utp",
    srcs = ["src"],
    iml_files = ["utp.iml"],
    lint_enabled = False,  # List can't handle addGradleAndroidTestListener.gradle from resources
    resources = ["resources"],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:com.intellij.gradle",
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/utp:libstudio.android-test-engine-result-listener-proto",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//tools/adt/idea/.idea/libraries:test-result-proto",
    ],
)

# Export addGradleAndroidTestListener.gradle init script for integration tests.
exports_files(
    ["resources/utp/addGradleAndroidTestListener.gradle"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
jvm_import(
    name = "libstudio.android-test-engine-result-listener-proto",
    jars = ["//tools/base/android-test/android-test-engine-result-listener:libstudio.android-test-engine-result-listener-proto.jar"],
    visibility = ["//visibility:public"],
)
