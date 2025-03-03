load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "app-inspection.inspectors.network.model",
    srcs = ["src"],
    iml_files = ["app-inspection.inspectors.network.model.iml"],
    lint_baseline = "lint_baseline.xml",
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "//tools/adt/idea/.idea/libraries:kotlin-test[test]",
        "//tools/adt/idea/app-inspection/api:app-inspection.api",
        "//tools/adt/idea/app-inspection/inspector/api:app-inspection.inspector.api",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//tools/adt/idea/.idea/libraries:network_inspector_java_proto",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/inspectors-common/api:intellij.android.inspectors-common.api",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/codenavigation",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[test]",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/base/annotations:studio.android.sdktools.android-annotations",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[test]",
        "//prebuilts/tools/common/m2:brotli-dec-0.1.2",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin[test]",
    ],
)
