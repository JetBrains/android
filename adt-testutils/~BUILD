load("//tools/base/bazel:bazel.bzl", "iml_module")

exports_files(["test-log.properties"])

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.adt.testutils",
    data = [
        ":test-log.properties",  # Used for idea.log.config.properties.file.
        "//prebuilts/studio/jdk:mock-jdk17",  # Used by test descriptors.
        "//tools/adt/idea/studio:required_jvm_args",  # Used to compute JNA path
    ],
    iml_files = ["intellij.android.adt.testutils.iml"],
    test_srcs = ["src/main/java"],
    visibility = ["//visibility:public"],
    exports = ["@intellij//:test-framework"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/adt/idea/design-plugin/descriptor:intellij.android.design-plugin.descriptor",
        "//tools/adt/idea/android-plugin/descriptor:intellij.android.plugin.descriptor",
        "//tools/adt/idea/.idea/libraries:opentest4j",
        "//tools/adt/idea/studio:studio-sdk-all-modules",
    ],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:com.intellij.gradle",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[test]",
        "//tools/base/repository:studio.android.sdktools.repository[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "@intellij//:org.toml.lang[test]",
        "@intellij//:test-framework[test]",
    ],
)
