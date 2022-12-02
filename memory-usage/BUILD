load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "memory-usage",
    srcs = ["src/main/java"],
    enable_tests = False,
    iml_files = ["memory-usage.iml"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
    ],
)

java_test(
    name = "tools.memory-usage_tests",
    size = "small",
    data = ["//tools/adt/idea/memory-usage/src/main/native:liblightweight_heap_traverse"],
    jvm_flags = ["-agentpath:$(location //tools/adt/idea/memory-usage/src/main/native:liblightweight_heap_traverse)"],
    test_class = "com.android.tools.memory.usage.LightweightHeapTraverseTest",
    runtime_deps = [
        ":memory-usage_testlib",
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
        "//tools/base/testutils:tools.testutils",
        "@maven//:junit.junit",
    ],
)
