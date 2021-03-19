load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "utp",
    srcs = ["src"],
    iml_files = ["utp.iml"],
    resources = ["resources"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle",
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/utp:libstudio.android-test-plugin-result-listener-gradle-proto",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//prebuilts/tools/common/m2/repository/com/google/testing/platform/core-proto/0.0.8-alpha02:utp-core-proto",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
    ],
)

# Export addGradleAndroidTestListener.gradle init script for integration tests.
genrule(
    name = "addGradleAndroidTestListener",
    testonly = 1,
    srcs = ["resources/utp/addGradleAndroidTestListener.gradle"],
    outs = ["addGradleAndroidTestListener.gradle"],
    cmd = "cp $< $@",
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
java_import(
    name = "libstudio.android-test-plugin-result-listener-gradle-proto",
    jars = ["//tools/base/utp/android-test-plugin-result-listener-gradle-proto:libstudio.android-test-plugin-result-listener-gradle-proto.jar"],
    visibility = ["//visibility:public"],
)
