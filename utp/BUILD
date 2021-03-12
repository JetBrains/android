load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "utp",
    srcs = ["src"],
    iml_files = ["utp.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle",
        "//prebuilts/studio/intellij-sdk:studio-sdk",
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
