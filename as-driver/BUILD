load("//tools/base/bazel:bazel.bzl", "iml_module")

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
    ],
)

load("//tools/base/bazel:jvm_import.bzl", "jvm_import")

# managed by go/iml_to_build
jvm_import(
    name = "asdriver_proto",
    jars = ["//tools/adt/idea/as-driver/proto:asdriver_proto.jar"],
    visibility = ["//visibility:public"],
)
