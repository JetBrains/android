load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "uitest-framework-bazel",
    iml_files = ["uitest-framework-bazel.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/json",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/java/compiler/instrumentation-util-8",
        "//tools/idea/plugins/maven:maven2-server-impl",
        "//tools/vendor/intellij/cidr:doxygen",
        "//tools/idea:community-main",
        "//tools/idea/jps/javac-ref-scanner-8",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/.idea/libraries:commons-lang"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/uitest-framework[module, test]",
        "//tools:fest-swing[module, test]",
        "//tools/idea/platform/external-system-api[module, test]",
        "//tools/idea/java/openapi[module, test]",
        "//tools/idea/platform/platform-impl[module, test]",
        "@blaze//third_party/intellij/bazel/plugin/aswb:aswb_bazel[module, test]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//prebuilts/tools/common/m2/repository/junit/junit/4.12:jar",
        "//tools/idea/.idea/libraries:Guava",
    ],
)
