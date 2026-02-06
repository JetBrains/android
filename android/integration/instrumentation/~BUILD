load("//tools/adt/idea/android/integration:build_defs.bzl", "INTEGRATION_TEST_GRADLE_VERSION")
load("//tools/base/bazel:bazel.bzl", "iml_module", "iml_test")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.core.integration.instrumentation",
    enable_tests = False,
    iml_files = ["intellij.android.core.integration.instrumentation.iml"],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "@intellij//:intellij-sdk",
        "//prebuilts/tools/common/m2:junit-4.12",
        "//tools/adt/idea/as-driver:as-driver.utils",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/base/perf-logger:studio.perf-logger",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/platform-performance-testing",
        "//tools/adt/idea/as-driver:as-driver.integration",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = ["@intellij//:com.intellij.java"],
)

iml_test(
    name = "BuildAndRunInstrumentedTest",
    timeout = "long",
    data = [
        "//prebuilts/studio/sdk:build-tools/33.0.1",
        "//prebuilts/studio/sdk:emulator",
        "//prebuilts/studio/sdk:platform-tools",
        "//tools/adt/idea/android/integration:instrumenttestapp_project_model",
        "//tools/adt/idea/android/integration:run_instrumented_test_project_deps",
        INTEGRATION_TEST_GRADLE_VERSION,
        "@system_image_android-33_aosp_atd_x86_64//:x86_64-android-33AospAtd-images",
    ],
    module = ":intellij.android.core.integration.instrumentation",
    tags = [
        "studio-e2e-test",
    ],
    tags_linux = [
        "block-network",
    ],
    tags_mac = [],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.idea.instrumentation.BuildAndRunInstrumentedTest",
)

iml_test(
    name = "MultipleDevicesInstrumentedTest",
    timeout = "long",
    data = [
        "//prebuilts/studio/sdk:build-tools/33.0.1",
        "//prebuilts/studio/sdk:emulator",
        "//prebuilts/studio/sdk:platform-tools",
        "//tools/adt/idea/android/integration:instrumenttestapp_project_model",
        "//tools/adt/idea/android/integration:run_instrumented_test_project_deps",
        INTEGRATION_TEST_GRADLE_VERSION,
        "@system_image_android-33_aosp_atd_x86_64//:x86_64-android-33AospAtd-images",
    ],
    module = ":intellij.android.core.integration.instrumentation",
    tags = [
        "studio-e2e-test",
    ],
    tags_linux = ["block-network"],
    tags_mac = [],
    tags_windows = [
        "block-network",
        "manual",  # RBE environment doesn't allow emulation (requires nested virtualization).
    ],
    test_class = "com.android.tools.idea.instrumentation.MultipleDevicesInstrumentedTest",
)
