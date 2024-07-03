load("//tools/adt/idea/studio:studio.bzl", "iml_studio_test")
load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:jvm_import.bzl", "jvm_import")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")
load("//tools/base/intellij-bazel:intellij.bzl", "intellij_plugin")

java_binary(
    name = "deploy_env",
    main_class = "NoMain",
    runtime_deps = [
        "@intellij//:intellij-sdk",
    ],
)

kotlin_library(
    name = "wrapper_class_file",
    srcs = glob(["plugin/src/com/android/tools/asdriver/plugin/*.kt"]),
)

intellij_plugin(
    name = "asdriver.plugin",
    directory = "asdriver.plugin",
    libs = [
        "wrapper_class_file",
        "//tools/adt/idea/bleak:intellij.android.bleak",
        "//tools/adt/idea/bleak-config:intellij.android.bleak-config",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//tools/adt/idea/as-driver:asdriver_proto",
        "//tools/adt/idea/.idea/libraries:studio-grpc",
        "//tools/adt/idea/adt-ui-compose:intellij.android.adt.ui.compose",
    ],
    modules = {"asdriver-plugin.jar": [":as-driver.plugin"]},
    platforms = [
        "studio-sdk",
    ],
    plugin_id = "com.android.tools.asdriver",
    deps = [
        "@intellij//:com.intellij.java",
        "@intellij//:com.jetbrains.performancePlugin",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "as-driver.utils",
    data = [
        "//prebuilts/studio/intellij-sdk:performanceTesting.zip",
        "//prebuilts/studio/sdk:platforms/android-33",
        "//tools/adt/idea/as-driver:asdriver.plugin",
        "//tools/base/threading-agent:threading_agent.jar",
        "//tools/vendor/google/testing/display:ffmpeg_files",
        "//tools/vendor/google/testing/display:xvfb",
    ],
    iml_files = ["utils/as-driver.utils.iml"],
    test_srcs = ["utils/testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/tools/common/m2:junit-4.12",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/base/repository:studio.android.sdktools.repository[module, test]",
        "//tools/adt/idea/.idea/libraries:studio-grpc[test]",
        "//tools/adt/idea/.idea/libraries:studio-proto[test]",
        "//tools/adt/idea/as-driver:asdriver_proto",
        "@intellij//:intellij-sdk[test]",
        "@intellij//:com.intellij.java",
        "//tools/base/common:studio.android.sdktools.common[module, test]",
        "//tools/base/perf-logger:studio.perf-logger[module, test]",
    ],
)

# managed by go/iml_to_build
jvm_import(
    name = "asdriver_proto",
    jars = ["//tools/adt/idea/as-driver/proto:asdriver_proto.jar"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
iml_module(
    name = "as-driver.integration",
    enable_tests = False,
    iml_files = ["integration/as-driver.integration.iml"],
    test_srcs = ["integration/testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/tools/common/m2:junit-4.12",
        "//tools/adt/idea/as-driver:as-driver.utils[module, test]",
        "@intellij//:intellij-sdk[test]",
        "@intellij//:com.intellij.java",
    ],
)

iml_studio_test(
    name = "ThreadingCheckerTest",
    module = ":as-driver.integration",
    test_class = "com.android.tools.asdriver.tests.integration.ThreadingCheckerTest",
)

# managed by go/iml_to_build
iml_module(
    name = "as-driver.plugin",
    srcs = ["plugin/src"],
    iml_files = ["plugin/as-driver.plugin.iml"],
    resources = ["plugin/resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/as-driver:asdriver_proto",
        "//tools/adt/idea/.idea/libraries:studio-grpc",
        "//tools/adt/idea/bleak:intellij.android.bleak[module]",
        "//tools/adt/idea/bleak-config:intellij.android.bleak-config[module]",
        "@intellij//:com.jetbrains.performancePlugin",
        "//tools/adt/idea/adt-ui-compose:intellij.android.adt.ui.compose[module]",
    ],
)
