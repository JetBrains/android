load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "app-inspection.inspectors.network.ide",
    srcs = ["src"],
    iml_files = ["app-inspection.inspectors.network.ide.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/app-inspection/inspector/ide:app-inspection.inspector.ide",
        "//tools/adt/idea/app-inspection/inspector/api:app-inspection.inspector.api",
        "//tools/adt/idea/artwork:intellij.android.artwork",
        "//tools/adt/idea/app-inspection/inspectors/network/view:app-inspection.inspectors.network.view",
        "//tools/adt/idea/app-inspection/inspectors/network/model:app-inspection.inspectors.network.model",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model",
        "//tools/adt/idea/native-symbolizer",
        "//tools/adt/idea/inspectors-common/api-ide:intellij.android.inspectors-common.api-ide",
        "//tools/adt/idea/inspectors-common/ui:intellij.android.inspectors-common.ui",
        "//tools/adt/idea/inspectors-common/api:intellij.android.inspectors-common.api",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/codenavigation",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "app-inspection.inspectors.network.ide.tests",
    iml_files = ["app-inspection.inspectors.network.ide.tests.iml"],
    test_class = "com.android.tools.idea.appinspection.inspectors.network.NetworkIdeTestSuite",
    test_data = glob(["testData/**"]),
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/inspectors-common/api:intellij.android.inspectors-common.api[test]",
        "//tools/adt/idea/inspectors-common/ui:intellij.android.inspectors-common.ui[test]",
        "//tools/adt/idea/app-inspection/inspectors/network/ide:app-inspection.inspectors.network.ide[test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[test]",
        "//tools/adt/idea/app-inspection/inspectors/network/view:app-inspection.inspectors.network.view[test]",
        "//tools/adt/idea/app-inspection/inspectors/network/model:app-inspection.inspectors.network.model[test]",
        "//tools/adt/idea/inspectors-common/api-ide:intellij.android.inspectors-common.api-ide[test]",
        "//tools/adt/idea/codenavigation",
    ],
)
