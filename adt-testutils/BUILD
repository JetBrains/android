load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "adt-testutils",
    iml_files = ["adt-testutils.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/RegExpSupport",
        "//tools/idea/platform/lvcs-impl",
        "//tools/idea/xml/xml-structure-view-impl",
        "//tools/idea/spellchecker",
        "//tools/idea/xml/relaxng",
        "//tools/idea/json",
        "//tools/idea/images",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/configuration-store-impl",
        "//tools/idea/platform/built-in-server",
    ],
    test_srcs = ["src/main/java"],
    test_tags = ["manual"],  # src/main/java is TEST scoped but contains no runnable tests
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/annotations[module, test]",
        "//tools/idea/platform/platform-impl[module, test]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/base/repository:studio.repository[module, test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:Gradle[test]",
        "//tools/idea/.idea/libraries:jna[test]",
    ],
)
