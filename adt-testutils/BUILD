load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.adt.testutils",
    data = [
        ":test-log.xml",  # Used for idea.log.config.file.
        "//tools/adt/idea/studio:required_jvm_args",  # Used to compute JNA path
    ],
    iml_files = ["intellij.android.adt.testutils.iml"],
    test_srcs = ["src/main/java"],
    visibility = ["//visibility:public"],
    exports = ["//prebuilts/studio/intellij-sdk:studio-sdk-test-framework"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/base/repository:studio.android.sdktools.repository[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-toml[test]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-test-framework[test]",
    ],
)
