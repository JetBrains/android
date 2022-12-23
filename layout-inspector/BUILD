load("//tools/base/bazel:bazel.bzl", "iml_module")

config_setting(
    name = "windows",
    values = {"host_cpu": "x64_windows"},
    visibility = ["//visibility:public"],
)

config_setting(
    name = "mac",
    values = {"host_cpu": "darwin"},
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.layout-inspector",
    srcs = ["src"],
    iml_files = ["intellij.android.layout-inspector.iml"],
    lint_baseline = "lint_baseline.xml",
    resources = ["resources"],
    test_srcs = ["testingSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:kotlin-test[test]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module]",
        "//tools/adt/idea/deploy:intellij.android.deploy[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/layout-ui:intellij.android.layout-ui[module]",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module]",
        "//tools/adt/idea/android-adb:intellij.android.adb[module]",
        "//tools/adt/idea/.idea/libraries:studio-grpc",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//tools/adt/idea/wizard:intellij.android.wizard[module]",
        "//tools/adt/idea/wizard-model:intellij.android.wizard.model[module]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/base/dynamic-layout-inspector/common:studio.dynamic-layout-inspector.common[module]",
        "//tools/adt/idea/app-inspection/api:app-inspection.api[module]",
        "//tools/adt/idea/app-inspection/ide:app-inspection.ide[module]",
        "//tools/adt/idea/app-inspection/inspector/api:app-inspection.inspector.api[module]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
        "//tools/adt/idea/.idea/libraries:layoutinspector-skia-proto",
        "//tools/adt/idea/.idea/libraries:layoutinspector-view-proto",
        "//tools/adt/idea/android-transport:intellij.android.transport[module]",
        "//tools/adt/idea/.idea/libraries:transport-proto",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/execution/common:intellij.android.execution.common[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.layout-inspector.tests",
    iml_files = ["intellij.android.layout-inspector.tests.iml"],
    test_class = "com.android.tools.idea.layoutinspector.LayoutInspectorTestSuite",
    test_data = [
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/layout-inspector/testData",
    ] + select({
        "windows": [":skiaparser-test.dll"],
        "mac": [":libskiaparser-test.dylib"],
        "//conditions:default": [":libskiaparser-test.so"],
    }),
    test_jvm_flags = ["-Djava.library.path=tools/adt/idea/layout-inspector"],
    test_resources = ["testResources"],
    test_srcs = ["testSrc"],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:kotlin-test[test]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/base/common:studio.android.sdktools.common[module, test]",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module, test]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/layout-inspector:intellij.android.layout-inspector[module, test]",
        "//tools/adt/idea/layout-ui:intellij.android.layout-ui[module, test]",
        "//tools/adt/idea/android-adb:intellij.android.adb[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.extensions[module, test]",
        "//tools/adt/idea/.idea/libraries:transport-proto[test]",
        "//tools/adt/idea/.idea/libraries:studio-proto[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/android-transport:intellij.android.transport[module, test]",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing[module, test]",
        "//tools/analytics-library/tracker:analytics-tracker[module, test]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto[test]",
        "//tools/adt/idea/.idea/libraries:studio-grpc[test]",
        "//tools/adt/idea/transport-database:intellij.android.transportDatabase[module, test]",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver[module, test]",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module, test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module, test]",
        "//tools/base/dynamic-layout-inspector/common:studio.dynamic-layout-inspector.common[module]",
        "//tools/adt/idea/app-inspection/api:app-inspection.api[module, test]",
        "//tools/adt/idea/app-inspection/ide:app-inspection.ide[module, test]",
        "//tools/adt/idea/app-inspection/inspector/api:app-inspection.inspector.api[module, test]",
        "//tools/adt/idea/layout-inspector:skiaparser_test_support",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
        "//tools/adt/idea/.idea/libraries:layoutinspector-skia-proto",
        "//tools/adt/idea/.idea/libraries:layoutinspector-view-proto",
        "//tools/adt/idea/deploy:intellij.android.deploy[module, test]",
        "//tools/adt/idea/execution/common:intellij.android.execution.common[module, test]",
    ],
)

cc_library(
    name = "skiaparser_test_support",
    srcs = ["testSrc/native/skia_parser_test_java_jni.cc"],
    copts = [
        "-DSK_USER_CONFIG_HEADER=\\\"StudioConfig.h\\\"",
    ],
    linkstatic = 1,
    deps = [
        "//prebuilts/studio/jdk:jni_headers",
        "//tools/base/dynamic-layout-inspector/skia:skia-lib",
        "//tools/base/dynamic-layout-inspector/skia/proto:layout_inspector_skia_cc_proto",
        "@freetype_repo//:libft2",
        "@libpng_repo//:libpng",
    ],
    alwayslink = 1,
)

cc_binary(
    name = "libskiaparser-test.dylib",
    linkshared = 1,
    tags = [
        "no_linux",
        "no_windows",
    ],
    deps = [
        ":skiaparser_test_support",
    ],
)

cc_binary(
    name = "libskiaparser-test.so",
    linkshared = 1,
    tags = [
        "no_mac",
        "no_windows",
    ],
    deps = [
        ":skiaparser_test_support",
    ],
)

cc_binary(
    name = "skiaparser-test.dll",
    linkshared = 1,
    tags = [
        "no_linux",
        "no_mac",
    ],
    deps = [
        ":skiaparser_test_support",
    ],
)
