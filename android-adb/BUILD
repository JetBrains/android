load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.adb",
    srcs = ["src"],
    iml_files = ["intellij.android.adb.iml"],
    lint_baseline = "lint_baseline.xml",
    resources = ["resources"],
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
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/base/adblib:studio.android.sdktools.adblib",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/android-adb:intellij.android.adb",
        "//tools/adt/idea/android-adb:intellij.android.adb.testUtil",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android-common:intellij.android.common.tests",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test",
        "//tools/adt/idea/.idea/libraries:mockito",
        "@intellij//:com.intellij.gradle",
        "@intellij//:intellij-sdk",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/.idea/libraries:zxing-core",
        "//tools/base/adblib-tools:studio.android.sdktools.adblib.tools",
        "//tools/base/jdwp-tracer:studio.android.sdktools.jdwptracer",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
    ],
    test_friends = ["//tools/adt/idea/android-adb:intellij.android.adb"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:com.intellij.java",
        "//tools/base/device-provisioner:studio.android.sdktools.device-provisioner",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.adb.testUtil",
    iml_files = ["intellij.android.adb.testUtil.iml"],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "@intellij//:intellij-sdk",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/android-adb:intellij.android.adb",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
    ],
    test_srcs = ["testUtil"],
    test_tags = ["manual"],  # testUtil is TEST scoped but contains no runnable tests
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = ["@intellij//:com.intellij.java"],
)
