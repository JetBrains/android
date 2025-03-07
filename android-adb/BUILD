load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.adb",
    srcs = ["src"],
    iml_files = ["intellij.android.adb.iml"],
    lint_baseline = "lint_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib",
        "//tools/base/adblib:studio.android.sdktools.adblib",
        "//tools/adt/idea/.idea/libraries:zxing-core",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/base/sdklib:studio.android.sdktools.sdklib",
        "@intellij//:com.intellij.platform.images",
        "//tools/base/pixelprobe:studio.android.sdktools.pixelprobe",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner",
        "//tools/base/process-monitor:studio.android.sdktools.process-monitor",
        "//tools/base/adblib-tools:studio.android.sdktools.adblib.tools",
        "//tools/base/adblib-ddmlibcompatibility:studio.android.sdktools.adblib.ddmlibcompatibility",
        "//tools/base/jdwp-tracer:studio.android.sdktools.jdwptracer",
        "//tools/base/jdwp-scache:studio.android.sdktools.jdwpscache",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/.idea/libraries:libadblib-tools-proto",
        "//tools/adt/idea/analytics",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common",
        "//tools/analytics-library/shared:analytics-shared",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.adb.tests",
    iml_files = ["intellij.android.adb.tests.iml"],
    test_agents = ["//tools/base/threading-agent:threading_agent.jar"],
    test_class = "com.android.tools.idea.adb.AndroidAdbTestSuite",
    test_data = [
        "//prebuilts/studio/sdk:platform-tools",
    ],
    test_friends = ["//tools/adt/idea/android-adb:intellij.android.adb"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:com.intellij.java",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner",
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "//tools/base/adblib:studio.android.sdktools.adblib[test]",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[test]",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver[test]",
        "//tools/base/flags:studio.android.sdktools.flags[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[test]",
        "//tools/adt/idea/android-adb:intellij.android.adb[test]",
        "//tools/adt/idea/android-adb:intellij.android.adb.testUtil[test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[test]",
        "//tools/adt/idea/artwork:intellij.android.artwork[test]",
        "//tools/adt/idea/android-common:intellij.android.common[test]",
        "//tools/adt/idea/android-common:intellij.android.common.tests[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[test]",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "@intellij//:com.intellij.gradle[test]",
        "@intellij//:intellij-sdk[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/.idea/libraries:zxing-core[test]",
        "//tools/base/adblib-tools:studio.android.sdktools.adblib.tools[test]",
        "//tools/base/jdwp-tracer:studio.android.sdktools.jdwptracer[test]",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing[test]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto[test]",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin[test]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.adb.testUtil",
    iml_files = ["intellij.android.adb.testUtil.iml"],
    test_srcs = ["testUtil"],
    test_tags = ["manual"],  # testUtil is TEST scoped but contains no runnable tests
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "@intellij//:intellij-sdk[test]",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[test]",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[test]",
        "//tools/adt/idea/android-adb:intellij.android.adb[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin[test]",
    ],
)
