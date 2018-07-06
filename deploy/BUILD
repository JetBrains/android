load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.deploy",
    srcs = ["src"],
    iml_files = ["intellij.android.deploy.iml"],
    test_data = glob(["testData/**"]),
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/annotations:intellij.platform.annotations.java5[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime[test]",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module]",
    ],
)
