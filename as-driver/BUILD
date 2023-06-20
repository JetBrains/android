load("//tools/base/bazel:bazel.bzl", "iml_module", "iml_test")

java_binary(
    name = "as_driver",
    srcs = glob(["agent/src/com/android/tools/asdriver/agent/*.java"]),
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
        "//prebuilts/studio/intellij-sdk:studio-sdk",
    ],
)

java_binary(
    name = "as_driver_inject",
    srcs = glob(["agent/src/com/android/tools/asdriver/inject/*.java"]),
    deploy_env = [
        ":deploy_env",
    ],
    main_class = "NoMain",
    deps = [
        "//tools/adt/idea/as-driver/proto:asdriver_proto",
        # "@maven//:io.grpc.grpc-all",
        "//tools/adt/idea/.idea/libraries:studio-grpc",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//prebuilts/studio/intellij-sdk:studio-sdk",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "as-driver.agent",
    srcs = ["agent/src"],
    iml_files = ["agent/as-driver.agent.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/as-driver:asdriver_proto",
        "//tools/adt/idea/.idea/libraries:studio-grpc",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "as-driver.utils",
    data = [
        "//prebuilts/studio/sdk:platforms/latest",
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
        "//prebuilts/studio/intellij-sdk:studio-sdk[test]",
        "//tools/base/common:studio.android.sdktools.common[module, test]",
        "//tools/base/perf-logger:studio.perf-logger[module, test]",
    ],
)

load("//tools/base/bazel:jvm_import.bzl", "jvm_import")

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
        "//prebuilts/studio/intellij-sdk:studio-sdk[test]",
    ],
)

iml_test(
    name = "ThreadingCheckerTest",
    data = select({
        "@platforms//os:linux": [
            "//tools/adt/idea/studio:android-studio.linux.zip",
        ],
        "@platforms//os:macos": [
            "//tools/adt/idea/studio:android-studio.mac.zip",
        ],
        "@platforms//os:windows": [
            "//tools/adt/idea/studio:android-studio.win.zip",
        ],
    }),
    module = ":as-driver.integration",
    test_class = "com.android.tools.asdriver.tests.integration.ThreadingCheckerTest",
)
