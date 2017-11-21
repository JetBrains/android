load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "uitest-framework-gradle",
    iml_files = ["uitest-framework-gradle.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/json",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/java/compiler/instrumentation-util-8",
        "//tools/idea/plugins/maven:maven2-server-impl",
        "//tools/adt/idea/adt-branding",
        "//tools/vendor/intellij/cidr:doxygen",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/gradle[module, test]",
        "//tools/adt/idea/uitest-framework[module, test]",
        "//tools/idea/platform/platform-impl[module, test]",
        "//tools/adt/idea/android[module, test]",
        "//tools:fest-swing[module, test]",
        "//prebuilts/tools/common/m2/repository/junit/junit/4.12:jar",
    ],
)
