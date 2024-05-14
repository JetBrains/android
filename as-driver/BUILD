load("//tools/adt/idea/studio:studio.bzl", "iml_studio_test")
load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:jvm_import.bzl", "jvm_import")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

java_binary(
    name = "as_driver",
    srcs = glob(["agent/testSrc/com/android/tools/asdriver/agent/*.java"]),
    deploy_manifest_lines = [
        "Premain-Class: com.android.tools.asdriver.agent.Agent",
        "Boot-Class-Path: as_driver.jar",
    ],
    main_class = "com.android.tools.asdriver.agent.Agent",
    resources = [
        "as_driver_inject_deploy.jar",
    ],
    visibility = ["//visibility:public"],
)

java_binary(
    name = "deploy_env",
    main_class = "NoMain",
    runtime_deps = [
        "@intellij//:intellij-sdk",
    ],
)

java_binary(
    name = "as_driver_inject",
    testonly = True,
    srcs = glob(["agent/testSrc/com/android/tools/asdriver/inject/*.java"]),
    deploy_env = [
        ":deploy_env",
    ],
    main_class = "NoMain",
    deps = [
        "wrapper_class_file",
        "//tools/adt/idea/as-driver/proto:asdriver_proto",
        # "@maven//:io.grpc.grpc-all",
        "//tools/adt/idea/.idea/libraries:studio-grpc",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "@intellij//:intellij-sdk",
        "//tools/adt/idea/bleak:intellij.android.bleak",
        "//tools/adt/idea/bleak-config:intellij.android.bleak-config",
        "//tools/adt/idea/bleak-config:intellij.android.bleak-config_testlib",
        "//tools/adt/idea/adt-ui-compose:intellij.android.adt.ui.compose",
    ],
)

kotlin_library(
    name = "wrapper_class_file",
    srcs = glob(["agent/testSrc/com/android/tools/asdriver/inject/*.kt"]),
)

# managed by go/iml_to_build
iml_module(
    name = "as-driver.agent",
    iml_files = ["agent/as-driver.agent.iml"],
    test_srcs = ["agent/testSrc"],
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
        "//tools/adt/idea/adt-ui-compose:intellij.android.adt.ui.compose[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "as-driver.utils",
    data = [
        "//prebuilts/studio/sdk:platforms/android-33",
        "//tools/adt/idea/as-driver:as_driver_deploy.jar",
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
